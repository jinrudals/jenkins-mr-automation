# Research: `abortOlderBuilds` Pipeline Step

**Feature**: 002-abort-older-builds | **Date**: 2026-04-29

## R1: How to enumerate running builds of the same Job

**Decision**: Use `Run.getParent()` to get the `Job`, then `Job.getBuilds()` to iterate and filter by `isBuilding()`.

**Rationale**: This is the same pattern used in `examples/mr.Jenkinsfile` (`currentBuild.rawBuild.parent` → `myJob.builds`). The `Job.getBuilds()` method returns a `RunList` in reverse chronological order. Filtering by `b.isBuilding() && b.getNumber() < currentBuildNumber` gives us the candidate set.

**Alternatives considered**:
- `Jenkins.instance.getQueue()`: Only covers queued (not yet running) builds. Does not help for already-executing builds.
- `Job.getLastBuild()` + walk backwards: Equivalent to `getBuilds()` iteration but more manual.

## R2: How to read upstream parent environment from a candidate build

**Decision**: Iterate `b.getCauses()`, find `Cause.UpstreamCause`, call `getUpstreamRun()`, then `getEnvironment(TaskListener.NULL)` on the parent run.

**Rationale**: Direct port of the `mr.Jenkinsfile` logic. `getUpstreamRun()` returns `null` if the parent build has been deleted — handled as skip-with-warning. `TaskListener.NULL` is used because we don't need to log from the candidate's parent context.

**Alternatives considered**:
- Reading candidate build's own `EnvActionImpl` (CPS env): Would require `setParentEnv` to have been called in the candidate build. The spec explicitly states we do NOT depend on that.
- `ParametersAction`: GitLab env vars are not passed as build parameters in this workflow.

## R3: How to abort a running build

**Decision**: Call `b.getExecutor().interrupt(Result.ABORTED)` for a clean abort, falling back to `b.doStop()` if no executor is available.

**Rationale**: `Executor.interrupt(Result.ABORTED)` is the preferred Jenkins API for programmatic build abortion — it sets the result, notifies listeners, and triggers `FlowInterruptedException` in Pipeline builds. `doStop()` (used in the original Jenkinsfile) is a coarser mechanism. Using `interrupt()` first provides cleaner abort semantics with proper result marking.

**Alternatives considered**:
- `b.doStop()`: Works but doesn't set a specific result or cause. Used as fallback.
- `b.delete()`: Destructive — removes the build record entirely. Not appropriate.

## R4: Matching logic — MR IID vs branch combination

**Decision**: MR IID match takes precedence. If both the current build and candidate have `gitlabMergeRequestIid` set and non-empty, compare by IID only. If MR IID is absent/empty in either build, fall back to `gitlabSourceBranch` + `gitlabTargetBranch` exact match.

**Rationale**: Per spec clarification — MR IID is the most precise identifier. Branch combination is a fallback for non-MR triggers. This matches the original `mr.Jenkinsfile` logic: `boolean sameMr = myMrIid && bMrIid == myMrIid; boolean sameBranch = !myMrIid && ...`.

**Alternatives considered**:
- Always match on both MR IID and branches: Over-restrictive, would miss cases where MR IID alone is sufficient.
- Match on branch only: Under-restrictive, different MRs can share the same source branch.

## R5: Current build's own MR/branch values — source

**Decision**: Read from the current build's own environment (`EnvVars` from `StepContext`), which contains values injected by `setParentEnv`.

**Rationale**: Per spec clarification (Session 2026-04-29 Q1) — `setParentEnv` is a prerequisite. The current build's `env.gitlabMergeRequestIid`, `env.gitlabSourceBranch`, `env.gitlabTargetBranch` are already available. No need to re-query the upstream parent for the current build.

**Alternatives considered**:
- Query current build's upstream parent directly: Redundant if `setParentEnv` has already run. Would add unnecessary API calls.

## R6: Thread safety and concurrent abort calls

**Decision**: No explicit synchronization. Build number comparison (`b.getNumber() < myBuildNumber`) provides natural ordering — the highest-numbered build always wins.

**Rationale**: If two builds of the same MR call `abortOlderBuilds` concurrently, the one with the lower build number will be aborted by the higher one. The lower one's own `abortOlderBuilds` call may also try to abort even-older builds, which is harmless (double-abort is idempotent). No locks needed.

**Alternatives considered**:
- File-based or Jenkins-global lock: Over-engineering for this use case. `interrupt()` is idempotent.

## R7: Parameter-based child jobs — candidate matching fallback

**Decision**: When reading a candidate build's MR/branch metadata, first try the upstream parent environment (primary path). If the upstream parent has no MR IID and no branch variables, fall back to reading the **candidate build's own environment** (`Run.getEnvironment()`), which includes its `ParametersAction` values.

**Rationale**: Two deployment patterns exist:
1. **Environment inheritance (Job2 pattern)**: Parent (Job1) has GitLab vars as parameters. Child (Job2) calls `setParentEnv()` to inherit them. The candidate's upstream parent environment contains the MR metadata.
2. **Parameter forwarding (Job3 pattern)**: Parent (Job1) explicitly passes GitLab vars as parameters to child (Job3) via `build job: 'job3', parameters: [...]`. The candidate build itself has the MR metadata in its own `ParametersAction`, but its upstream parent may or may not have them depending on how the parent is structured.

For Job3, the upstream parent's environment does contain the values (Job1 has them as parameters → `Run.getEnvironment()` includes `ParametersAction`). However, the candidate Job3 build also has them directly. The safest approach: try upstream parent first, then fall back to the candidate's own environment. This covers both patterns without requiring the Jenkinsfile author to choose.

**E2E validation**: E2E 2 covers the parameter-forwarding path where Job3 receives GitLab vars as its own parameters.

**Alternatives considered**:
- Only read upstream parent: Would work for both patterns (since Job1's parameters are in `getEnvironment()`), but is fragile if the parent job structure changes.
- Only read candidate's own env: Would miss cases where the candidate inherited vars via `setParentEnv` (CPS env, not in `Run.getEnvironment()`).
