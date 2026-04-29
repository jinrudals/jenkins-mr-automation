# Tasks: `abortOlderBuilds` Pipeline Step

**Input**: Design documents from `/specs/002-abort-older-builds/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**Tests**: Included — integration tests and E2E tests with `JenkinsRule` are required per constitution (III. Test-First) and plan.md Test Strategy.

**Organization**: Three user stories (US1: MR IID matching, US2: Branch combination fallback, US3: No-op safety). Tasks organized by phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[US1]**: User Story 1 — Abort Older Builds for the Same MR
- **[US2]**: User Story 2 — Abort Older Builds by Branch Combination
- **[US3]**: User Story 3 — No-Op When No Duplicates Exist

---

## Phase 1: Setup

**Purpose**: Snippet Generator config for the new step

- [x] T001 Create empty `src/main/resources/io/jenkins/plugins/mrautomation/AbortOlderBuildsStep/config.jelly` for Snippet Generator support

**Checkpoint**: Project structure ready for new step

---

## Phase 2: User Story 1 — Abort Older Builds for the Same MR (Priority: P1) 🎯 MVP

**Goal**: `abortOlderBuilds` scans running builds of the same Job, reads upstream parent env for MR IID, and aborts older builds with matching IID.

**Independent Test**: Trigger two builds of the same Job with the same `gitlabMergeRequestIid` in parent env. Verify older build is aborted.

### Implementation for User Story 1

- [x] T002 [US1] Implement `AbortOlderBuildsStep` (Step + `@DataBoundConstructor` + `SynchronousNonBlockingStepExecution` + `@Extension` Descriptor) in `src/main/java/io/jenkins/plugins/mrautomation/AbortOlderBuildsStep.java` — core logic: read current env for `gitlabMergeRequestIid`, iterate `Job.getBuilds()`, filter `isBuilding() && number < myNumber`, read candidate's upstream parent env via `getCauses()`/`getUpstreamRun()`/`getEnvironment()`, abort matches via `Executor.interrupt(Result.ABORTED)` with `doStop()` fallback, log aborted builds
- [x] T003 [US1] Implement unit/integration tests for MR IID matching in `src/test/java/io/jenkins/plugins/mrautomation/AbortOlderBuildsStepTest.java` — cover: same MR IID aborts older build, different MR IIDs no abort, three builds same IID aborts both older

**Checkpoint**: `mvn clean verify` passes — MR IID abort works

---

## Phase 3: User Story 2 — Abort by Branch Combination (Priority: P2)

**Goal**: When MR IID is absent, fall back to matching by `gitlabSourceBranch` + `gitlabTargetBranch`.

**Independent Test**: Trigger two builds with same source+target branch but no MR IID. Verify older build is aborted.

### Implementation for User Story 2

- [x] T004 [US2] Extend matching logic in `AbortOlderBuildsStep.java` — add branch combination fallback: when `myMrIid` is empty, match by `gitlabSourceBranch` + `gitlabTargetBranch` exact equality
- [x] T005 [US2] Add branch-matching tests in `AbortOlderBuildsStepTest.java` — cover: same branches no MR IID aborts, same source different target no abort

**Checkpoint**: `mvn clean verify` passes — branch fallback works

---

## Phase 4: User Story 3 — No-Op Safety (Priority: P3)

**Goal**: Step completes silently when no matches found or no upstream parent exists.

**Independent Test**: Single build calls `abortOlderBuilds`. Completes without error.

### Implementation for User Story 3

- [x] T006 [US3] Add no-op/edge-case tests in `AbortOlderBuildsStepTest.java` — cover: no-op single build, no-op no upstream parent, skip candidate with deleted parent (log warning), skip candidate with no MR IID and no branch vars

**Checkpoint**: `mvn clean verify` passes — all edge cases covered

---

## Phase 5: E2E Tests

**Purpose**: Validate full parent→child trigger chain with multi-job topology

- [x] T007 [P] E2E 1: Environment inheritance test in `AbortOlderBuildsStepTest.java` — Job1 (WorkflowJob, parameters: `gitlabMergeRequestIid=42`) → Job2 (pipeline: `setParentEnv(); abortOlderBuilds(); semaphore`) ×2 with same `UpstreamCause`. Assert build #1 ABORTED, build #2 SUCCESS
- [x] T008 [P] E2E 2: Parameter forwarding test in `AbortOlderBuildsStepTest.java` — Job1 (parameters: `gitlabMergeRequestIid=99`) → Job3 (own parameters + `setParentEnv(); abortOlderBuilds(); semaphore`) ×2 with same `UpstreamCause` + `ParametersAction`. Assert build #1 ABORTED, build #2 SUCCESS
- [x] T009 [P] E2E 3: Different MR IIDs test in `AbortOlderBuildsStepTest.java` — two Job1 builds with different IIDs trigger two Job2 builds. Assert both complete SUCCESS (no abort)

**Checkpoint**: `mvn clean verify` passes — all E2E scenarios green

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Manual E2E Jenkinsfiles and final validation

- [x] T010 [P] Create `examples/test-abort-parent.Jenkinsfile` — parent job with GitLab params that triggers child twice with `wait: false`
- [x] T011 [P] Create `examples/test-abort-child.Jenkinsfile` — child job calling `setParentEnv(); abortOlderBuilds()` then sleeping to allow overlap
- [x] T012 Run `mvn clean verify` and validate HPI artifact at `target/jenkins-mr-automation.hpi`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **US1 (Phase 2)**: Depends on Phase 1 (config.jelly must exist)
- **US2 (Phase 3)**: Depends on Phase 2 (extends AbortOlderBuildsStep.java)
- **US3 (Phase 4)**: Depends on Phase 2 (tests exercise existing step)
- **E2E (Phase 5)**: Depends on Phase 3 (needs both MR IID and branch matching)
- **Polish (Phase 6)**: Depends on Phase 5

### Within Phases

- T007, T008, T009 can run in parallel (independent test methods)
- T010, T011 can run in parallel (different files)

### Parallel Opportunities

```text
# Phase 5 — parallel E2E tests:
T007: E2E 1 (environment inheritance)
T008: E2E 2 (parameter forwarding)
T009: E2E 3 (different MR IIDs)

# Phase 6 — parallel Jenkinsfiles:
T010: examples/test-abort-parent.Jenkinsfile
T011: examples/test-abort-child.Jenkinsfile
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: User Story 1 (T002–T003)
3. **STOP and VALIDATE**: `mvn clean verify` — MR IID abort works
4. Deploy HPI and test manually

### Incremental Delivery

1. Setup → config.jelly ready
2. US1 → MR IID matching works → Deploy (MVP!)
3. US2 → Branch fallback added
4. US3 → Edge cases covered
5. E2E → Full chain validated
6. Polish → Manual Jenkinsfiles added

---

## Notes

- All tests in single file `AbortOlderBuildsStepTest.java` — follows existing pattern from `SetParentEnvStepTest.java`
- E2E tests use `SemaphoreStep` (from `workflow-support` test dependency) to hold builds in running state while the newer build aborts them
- Step implementation is a single file `AbortOlderBuildsStep.java` — US2 extends the matching logic in the same file, not a separate class
- Commit after each phase with updated `tasks.md` checkboxes per constitution (Development Workflow)
