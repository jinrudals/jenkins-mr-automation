# Feature Specification: `abortOlderBuilds` Pipeline Step

**Feature Branch**: `002-abort-older-builds`  
**Created**: 2026-04-29  
**Status**: Draft  
**Input**: User description: "abortOlderBuilds pipeline step to abort older running builds for the same MR or branch combination"

## Background & Motivation

The current implementation lives in [`examples/olds/MR_Template.Jenkinsfile`](../../examples/olds/MR_Template.Jenkinsfile) as a `@NonCPS def abortOlderBuilds(...)` function. This function calls sandbox-restricted APIs — `currentBuild.rawBuild.parent`, `b.getCauses()`, `cause.getUpstreamRun()`, `b.doStop()` — which **require Script Security approval** on every Jenkins instance where the Jenkinsfile is used. Each API signature must be individually approved by a Jenkins administrator, and approvals must be re-granted whenever the function signature or call pattern changes.

This plugin step replaces that `@NonCPS` function so that all sandbox-restricted API calls are handled inside the plugin, eliminating the need for Script Security approvals entirely.

## User Scenarios & Testing

### User Story 1 - Abort Older Builds for the Same MR (Priority: P1)

A pipeline author calls `abortOlderBuilds` at the start of a child MR job. The step inspects all other currently running builds of the same Job and aborts any whose upstream parent environment indicates the same MR IID. This ensures only the latest push for a given MR is built, saving CI resources and avoiding stale results.

**Why this priority**: Duplicate builds for the same MR are the most common source of wasted CI time. Aborting by MR IID is the most precise matching criterion and directly maps to the GitLab webhook trigger model.

**Independent Test**: Trigger two builds of the same Job with the same `gitlabMergeRequestIid` in the parent environment. Verify the older build is aborted and the newer build continues.

**Acceptance Scenarios**:

1. **Given** two builds of the same Job are running and both have the same `gitlabMergeRequestIid` in their upstream parent's environment, **When** the newer build calls `abortOlderBuilds`, **Then** the older build is aborted.
2. **Given** two builds of the same Job are running with different `gitlabMergeRequestIid` values, **When** `abortOlderBuilds` is called, **Then** neither build is aborted.
3. **Given** three builds are running for the same MR IID, **When** the newest build calls `abortOlderBuilds`, **Then** both older builds are aborted.

---

### User Story 2 - Abort Older Builds by Branch Combination (Priority: P2)

When MR IID is not available (e.g., branch-based triggers without MR context), the step falls back to matching by source branch + target branch combination from the upstream parent environment. Builds with the same source and target branch pair are considered duplicates.

**Why this priority**: Branch-based matching provides coverage for pipelines triggered outside the MR webhook flow, ensuring the step is useful in broader CI configurations.

**Independent Test**: Trigger two builds with the same `gitlabSourceBranch` and `gitlabTargetBranch` in the parent environment but no MR IID. Verify the older build is aborted.

**Acceptance Scenarios**:

1. **Given** two builds are running with the same `gitlabSourceBranch` and `gitlabTargetBranch` in their upstream parent environment and no `gitlabMergeRequestIid`, **When** `abortOlderBuilds` is called, **Then** the older build is aborted.
2. **Given** two builds have the same source branch but different target branches, **When** `abortOlderBuilds` is called, **Then** neither build is aborted.

---

### User Story 3 - No-Op When No Duplicates Exist (Priority: P3)

The step completes silently when no other running builds match the current build's MR or branch criteria. This ensures the step is safe to include unconditionally in all MR pipelines.

**Why this priority**: Safe no-op behavior is essential for unconditional use in shared pipeline templates.

**Independent Test**: Run a single build and call `abortOlderBuilds`. Verify the step completes without error and the build continues normally.

**Acceptance Scenarios**:

1. **Given** only one build is running for the Job, **When** `abortOlderBuilds` is called, **Then** the step completes without error and the build continues.
2. **Given** the current build has no upstream parent, **When** `abortOlderBuilds` is called, **Then** the step completes without error (no-op).

---

### Edge Cases

- What happens when the upstream parent build has been deleted before `abortOlderBuilds` reads its environment? → The build is skipped (not aborted) with a log warning.
- What happens when a build has multiple upstream parents? → Use the most recent (last) upstream build's environment, consistent with `setParentEnv` behavior.
- What happens when `abortOlderBuilds` is called concurrently by two builds of the same MR? → The build with the higher build number wins; the lower-numbered build is aborted. If both have the same build number (should not happen), neither is aborted.
- What happens when the upstream parent environment has no MR IID and no branch variables? → The build is skipped (not considered a match candidate).

## Requirements

### Functional Requirements

- **FR-001**: Plugin MUST provide an `abortOlderBuilds` pipeline step usable in both Scripted and Declarative pipelines.
- **FR-002**: The step MUST identify other running builds of the same Job.
- **FR-003**: For each running build, the step MUST read MR/branch metadata from that build's upstream parent environment using `getCauses()` and `getUpstreamRun()`.
- **FR-004**: The step MUST abort running builds that match by `gitlabMergeRequestIid` (primary match criterion).
- **FR-005**: When MR IID is not available for comparison, the step MUST fall back to matching by `gitlabSourceBranch` + `gitlabTargetBranch` combination.
- **FR-006**: The step MUST only abort builds with a lower build number than the current build.
- **FR-007**: The step MUST be a no-op (with no error) when no matching older builds are found.
- **FR-008**: The step MUST be a no-op (with no error) when the current build has no upstream parent.
- **FR-009**: The step MUST operate without requiring Script Security approvals — all sandbox-restricted API calls (`rawBuild`, `getCauses()`, `getUpstreamRun()`) are handled inside the plugin.
- **FR-010**: The step MUST log which builds were aborted (build number and match reason) for traceability.

### Key Entities

- **Current Build**: The build that invokes `abortOlderBuilds`. Its upstream parent environment provides the reference MR IID and branch values.
- **Candidate Build**: Any other running build of the same Job. Its upstream parent environment is inspected for matching MR IID or branch combination.
- **Upstream Parent Environment**: The environment variables of the build that triggered a given build, accessed via `Cause.UpstreamCause` → `getUpstreamRun()` → `getEnvironment()`. Contains `gitlabMergeRequestIid`, `gitlabSourceBranch`, `gitlabTargetBranch`.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Zero Script Security approvals required — `abortOlderBuilds` operates without sandbox approvals.
- **SC-002**: When multiple builds for the same MR are running, only the latest build continues; all older builds are terminated.
- **SC-003**: A Jenkinsfile using `abortOlderBuilds` eliminates manual build cancellation for duplicate MR pushes.
- **SC-004**: The step completes within 5 seconds under normal conditions (fewer than 50 concurrent builds of the same Job).
- **SC-005**: Aborted builds are clearly marked with the reason in their build log.

## Clarifications

### Session 2026-04-29

- Q: 현재 빌드의 MR/브랜치 정보를 어디서 읽는가? → A: 현재 빌드는 `setParentEnv`가 주입한 자신의 환경변수(`env.gitlabMergeRequestIid` 등)를 사용. 후보 빌드만 upstream parent를 직접 조회.
- Q: 기존 구현 참조 및 Script Security 문제 명시 → A: `examples/olds/MR_Template.Jenkinsfile`의 `@NonCPS abortOlderBuilds` 함수가 원본이며, sandbox-restricted API 호출로 인해 Script Security approval이 필요했음을 Background & Motivation 섹션에 명시.

## Assumptions

- The `setParentEnv` step (from 001-jenkins-mr-plugin) MUST have been called before `abortOlderBuilds` in the current build's pipeline. The current build reads its own `env.gitlabMergeRequestIid`, `env.gitlabSourceBranch`, `env.gitlabTargetBranch` (already injected by `setParentEnv`). Candidate builds' metadata is read by directly querying their upstream parent environment — those builds do not need to have called `setParentEnv`.
- MR IID matching takes precedence over branch matching. If MR IID is present in both builds, branch values are not compared.
- The step only considers builds of the same Job (not across different Jobs).
- Jenkins 2.387+ (LTS) is the minimum supported version, consistent with the plugin's baseline.
- The step does not wait for aborted builds to finish terminating — it issues the abort and continues.
