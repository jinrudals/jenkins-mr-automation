# Implementation Plan: `abortOlderBuilds` Pipeline Step

**Branch**: `002-abort-older-builds` | **Date**: 2026-04-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-abort-older-builds/spec.md`

## Summary

Add an `abortOlderBuilds` pipeline step to the existing Jenkins MR Automation Plugin. The step scans running builds of the same Job, compares MR IID (primary) or source+target branch (fallback) from each build's upstream parent environment, and aborts older duplicates. All sandbox-restricted API calls (`rawBuild`, `getCauses()`, `getUpstreamRun()`, `doStop()`) are encapsulated inside the plugin, eliminating Script Security approvals. Replaces the `@NonCPS abortOlderBuilds()` function in `examples/olds/MR_Template.Jenkinsfile`.

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: `workflow-step-api` (already in pom.xml)
**Storage**: N/A
**Testing**: JUnit + `JenkinsRule` (jenkins-test-harness, already configured)
**Target Platform**: Jenkins 2.479.3 LTS (as declared in pom.xml)
**Project Type**: Jenkins plugin (HPI) — adding a new step to existing plugin
**Performance Goals**: Step execution < 1s for up to 50 concurrent builds of the same Job
**Constraints**: No Script Security approvals; no new runtime dependencies
**Scale/Scope**: Single pipeline step added to existing plugin; reuses existing package and build infrastructure

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Jenkins Plugin Conventions | ✅ PASS | Same plugin, same POM, `@Extension` step registration |
| II. Pipeline Step Contract | ✅ PASS | No parameters (`@DataBoundConstructor`), no-op when no parent/no matches, no unchecked exceptions |
| III. Test-First | ✅ PASS | JenkinsRule integration tests planned for happy-path + edge cases |
| IV. Sandbox Safety | ✅ PASS | All restricted APIs (`rawBuild`, `getCauses()`, `getUpstreamRun()`, `doStop()`) inside plugin code |
| V. Simplicity & YAGNI | ✅ PASS | Single class + Execution inner class, no abstraction layers. Shared code extraction not needed (only 2nd step) |
| Technology Constraints | ✅ PASS | Java 17, Jenkins 2.479.3, BOM 2.479.x, no new dependencies |
| Development Workflow | ✅ PASS | Feature branch `002-abort-older-builds`, tasks.md phases, `mvn verify` gate |

No violations. No Complexity Tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-abort-older-builds/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
src/
└── main/
    └── java/
        └── io/jenkins/plugins/mrautomation/
            ├── SetParentEnvStep.java          # (existing)
            ├── InjectEnvVarsAction.java       # (existing)
            └── AbortOlderBuildsStep.java      # NEW — Step + Execution + Descriptor

src/
└── test/
    └── java/
        └── io/jenkins/plugins/mrautomation/
            └── AbortOlderBuildsStepTest.java  # NEW — JenkinsRule integration tests
```

**Structure Decision**: Single new Java file in existing package. Follows the same pattern as `SetParentEnvStep.java` — Step class with inner `SynchronousNonBlockingStepExecution` and `@Extension DescriptorImpl`. No new packages, no shared utility extraction (only 2nd step, per Constitution V).

## Test Strategy

### Unit/Integration Tests (`AbortOlderBuildsStepTest.java` with JenkinsRule)

Standard JenkinsRule tests covering happy-path and edge cases (no-op, no parent, different MR IIDs, etc.). See spec acceptance scenarios.

### E2E Test Scenarios

E2E tests validate the full parent→child trigger chain with `abortOlderBuilds` aborting stale builds. These are also JenkinsRule-based but exercise the multi-job topology.

#### E2E 1: Environment inheritance — Job1(params) → Job2(setParentEnv) ×2, older Job2 aborted

**Topology**: `job1` (parent, pipeline with parameters) → `job2` (child, pipeline with `setParentEnv` + `abortOlderBuilds`)

**Flow**:
1. Create `job1` (WorkflowJob) with `gitlabMergeRequestIid=42`, `gitlabSourceBranch`, `gitlabTargetBranch` as **string parameters**
2. Build `job1` with those parameter values
3. Create `job2` (WorkflowJob) with pipeline: `setParentEnv(); abortOlderBuilds(); sleep()`
4. Trigger `job2` build #1 with `UpstreamCause(job1Build)` — it starts running and blocks on `sleep`
5. Trigger `job2` build #2 with `UpstreamCause(job1Build)` — same parent, same MR IID
6. Build #2's `abortOlderBuilds` should abort build #1
7. **Assert**: Build #1 is ABORTED, build #2 completes SUCCESS

**What this validates**: Job1 has GitLab vars as parameters. Job2 inherits them via `setParentEnv`. `abortOlderBuilds` reads candidate's upstream parent env (Job1's `getEnvironment()` which includes `ParametersAction`) to match MR IID.

#### E2E 2: Parameter forwarding — Job1(params) → Job3(own params) ×2, older Job3 aborted

**Topology**: `job1` (parent, pipeline with parameters) → `job3` (child, pipeline with own parameters + `setParentEnv` + `abortOlderBuilds`)

**Flow**:
1. Create `job1` (WorkflowJob) with GitLab vars as string parameters
2. Build `job1` with parameter values (`gitlabMergeRequestIid=99`, etc.)
3. Create `job3` (WorkflowJob) with its own `parameters { string(name: 'gitlabMergeRequestIid') ... }` and pipeline: `setParentEnv(); abortOlderBuilds(); sleep()`
4. Trigger `job3` build #1 with `UpstreamCause(job1Build)` + `ParametersAction(gitlabMergeRequestIid=99, ...)` — blocks on sleep
5. Trigger `job3` build #2 with same upstream cause and same parameters
6. Build #2's `abortOlderBuilds` should abort build #1
7. **Assert**: Build #1 is ABORTED, build #2 completes SUCCESS

**What this validates**: Job3 receives GitLab vars as its **own parameters** (not via `setParentEnv` inheritance). `abortOlderBuilds` must be able to match by reading the candidate build's upstream parent env (which also has the values since Job1 has them as parameters). This confirms the parameter-forwarding pattern works.

**Key difference from E2E 1**: Job3 has its own `parameters {}` block. The GitLab vars exist in both the candidate's own `ParametersAction` and its upstream parent's `ParametersAction`.

#### E2E 3: Different MR IIDs — no abort

**Flow**: Same as E2E 1 but with different `gitlabMergeRequestIid` values for each parent build (two separate Job1 builds with different IIDs). Neither child build should be aborted.

**Assert**: Both builds complete SUCCESS.

### Manual E2E Jenkinsfiles

`examples/` directory will contain Jenkinsfiles for manual validation on a real Jenkins instance (extending existing `test-parent.Jenkinsfile` and `test-child.Jenkinsfile`).
