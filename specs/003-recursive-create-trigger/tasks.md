# Tasks: `recursiveCreateAndTrigger` Pipeline Step

**Input**: Design documents from `/specs/003-recursive-create-trigger/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: Add `cloudbees-folder` dependency and create the step class skeleton

- [x] T001 Add `cloudbees-folder` dependency to `pom.xml` (groupId: `org.jenkins-ci.plugins`, artifactId: `cloudbees-folder`, no version — BOM-managed)
- [x] T002 Create `RecursiveCreateAndTriggerStep.java` skeleton with `@DataBoundConstructor(repoPath, defaultTemplate)`, `@DataBoundSetter` for `configuration`, `defaultTargetName`, `skipPattern`, inner `TemplateRule` class extending `AbstractDescribableImpl` with `@DataBoundConstructor(pattern, finalJobNamePattern, templateName)` and `@Extension @Symbol("templateRule")` descriptor, inner `Execution` class extending `SynchronousNonBlockingStepExecution<Void>`, and `@Extension` `DescriptorImpl` with `getFunctionName()` returning `"recursiveCreateAndTrigger"` in `src/main/java/io/jenkins/plugins/mrautomation/RecursiveCreateAndTriggerStep.java`
- [x] T003 [P] Create empty `config.jelly` for the step in `src/main/resources/io/jenkins/plugins/mrautomation/RecursiveCreateAndTriggerStep/config.jelly`
- [x] T004 [P] Create empty `config.jelly` for TemplateRule in `src/main/resources/io/jenkins/plugins/mrautomation/RecursiveCreateAndTriggerStep/TemplateRule/config.jelly`
- [x] T005 Verify build compiles: `mvn clean compile`

**Checkpoint**: Plugin compiles with the new step registered. `recursiveCreateAndTrigger` appears in Pipeline Snippet Generator (no logic yet).

---

## Phase 2: Foundational (Core Logic)

**Purpose**: Implement the shared internal methods used by all user stories — `${REPO}` resolution, folder hierarchy creation, and job copy with optimistic concurrency

- [x] T006 Implement `resolveRepoVariable(String pattern, String repoPath)` private method in `Execution` — replaces `${REPO}` with `repoPath` value in `RecursiveCreateAndTriggerStep.java`
- [x] T007 Implement `getOrCreateFolder(ItemGroup<?> parent, String name, TaskListener listener)` private method in `Execution` — check-then-create with `IllegalArgumentException` catch for race safety, fail with clear error if existing item is not a `Folder` in `RecursiveCreateAndTriggerStep.java`
- [x] T008 Implement `createFolderHierarchy(String targetJobPath, TaskListener listener)` private method in `Execution` — split path by `/`, iterate all segments except last, call `getOrCreateFolder` for each, return the final parent `Folder` in `RecursiveCreateAndTriggerStep.java`
- [x] T009 Implement `copyTemplateJob(Folder parent, String jobName, String templatePath, TaskListener listener)` private method in `Execution` — resolve template via `Jenkins.get().getItemByFullName()`, fail if not found (FR-015), call `parent.copy(template, jobName)` with `IllegalArgumentException` catch for race safety in `RecursiveCreateAndTriggerStep.java`
- [x] T010 Implement `triggerJob(String targetJobPath, Run<?,?> currentRun, TaskListener listener)` private method in `Execution` — resolve job, call `ParameterizedJobMixIn.scheduleBuild2(job, 0, new CauseAction(new Cause.UpstreamCause(currentRun)))`, log result in `RecursiveCreateAndTriggerStep.java`
- [x] T011 Verify build compiles: `mvn clean compile`

**Checkpoint**: All internal building blocks ready. No `run()` method wired yet.

---

## Phase 3: User Story 1 — Create Job from Template and Trigger (Priority: P1) 🎯 MVP

**Goal**: Full end-to-end flow: validate repoPath → resolve template via defaults → create folder hierarchy → copy template → trigger job

**Independent Test**: Call `recursiveCreateAndTrigger` with a valid repoPath and defaultTemplate. Verify folders created, job copied, job triggered.

### Implementation for User Story 1

- [x] T012 [US1] Implement `run()` method in `Execution`: validate `repoPath` non-blank (FR-013), resolve target name via `defaultTargetName` with `${REPO}` substitution, check if target job exists → skip to trigger (delegate to US2 path), else call `createFolderHierarchy` + `copyTemplateJob` + `triggerJob`, log each action (FR-012) in `RecursiveCreateAndTriggerStep.java`
- [x] T013 [US1] Write integration test `createJobFromTemplateAndTrigger` — create a template WorkflowJob, call step with repoPath + defaultTemplate, assert folder hierarchy exists, job copied, job triggered in `src/test/java/io/jenkins/plugins/mrautomation/RecursiveCreateAndTriggerStepTest.java`
- [x] T014 [US1] Write integration test `failsWhenRepoPathEmpty` — call step with empty repoPath, assert AbortException with clear message in `RecursiveCreateAndTriggerStepTest.java`
- [x] T015 [US1] Write integration test `failsWhenTemplateNotFound` — call step with non-existent template path, assert AbortException with clear message in `RecursiveCreateAndTriggerStepTest.java`
- [x] T016 [US1] Run `mvn clean verify` — all tests pass

**Checkpoint**: Core flow works. Plugin can create folder hierarchy, copy template, and trigger job.

---

## Phase 4: User Story 2 — Skip Creation When Job Already Exists (Priority: P1)

**Goal**: When target job already exists, skip creation and trigger directly

**Independent Test**: Pre-create a job at the target path. Call step. Verify no creation, job triggered.

### Implementation for User Story 2

- [x] T017 [US2] Ensure `run()` method checks `Jenkins.get().getItemByFullName(targetJobPath)` before creation — if exists, log skip and call `triggerJob` directly (already partially in T012, verify and complete) in `RecursiveCreateAndTriggerStep.java`
- [x] T018 [US2] Write integration test `skipCreationWhenJobExists` — pre-create job at target path, call step, assert no new folders created, existing job triggered in `RecursiveCreateAndTriggerStepTest.java`
- [x] T019 [US2] Run `mvn clean verify` — all tests pass

**Checkpoint**: Idempotent behavior verified. Repeated calls trigger existing job without re-creation.

---

## Phase 5: User Story 3 — Rule-Based Template Resolution (Priority: P2)

**Goal**: Ordered configuration rules determine template and target name per repoPath pattern

**Independent Test**: Provide multiple rules, verify first-match wins and `${REPO}` resolves correctly.

### Implementation for User Story 3

- [x] T020 [US3] Implement rule matching logic in `run()` — iterate `configuration` list, match `repoPath` against each rule's `pattern` regex, use first match's `finalJobNamePattern` + `templateName`, fall back to `defaultTargetName` + `defaultTemplate` if no match (FR-005, FR-014) in `RecursiveCreateAndTriggerStep.java`
- [x] T021 [US3] Write integration test `ruleBasedTemplateFirstMatch` — two rules, first matches, verify correct template used in `RecursiveCreateAndTriggerStepTest.java`
- [x] T022 [P] [US3] Write integration test `ruleBasedTemplateFallthrough` — two rules, first doesn't match, second does, verify second template used in `RecursiveCreateAndTriggerStepTest.java`
- [x] T023 [P] [US3] Write integration test `defaultFallbackWhenNoRuleMatches` — rules that don't match, verify defaultTemplate + defaultTargetName used in `RecursiveCreateAndTriggerStepTest.java`
- [x] T024 [P] [US3] Write integration test `repoVariableResolution` — verify `${REPO}` replaced correctly in both `finalJobNamePattern` and `defaultTargetName` in `RecursiveCreateAndTriggerStepTest.java`
- [x] T025 [US3] Run `mvn clean verify` — all tests pass

**Checkpoint**: Rule-based template resolution works with first-match semantics and default fallback.

---

## Phase 6: User Story 4 — Race-Safe Concurrent Creation (Priority: P2)

**Goal**: Concurrent invocations for the same repo don't produce errors or duplicate jobs

**Independent Test**: Simulate two concurrent calls for the same repoPath. Verify one creates, both trigger.

### Implementation for User Story 4

- [x] T026 [US4] Write integration test `concurrentCreationRaceSafe` — use two threads or sequential calls where second call hits the already-exists path, verify no error and both trigger the job in `RecursiveCreateAndTriggerStepTest.java`
- [x] T027 [US4] Run `mvn clean verify` — all tests pass (race safety already implemented in T007/T009 via `IllegalArgumentException` catch)

**Checkpoint**: Optimistic concurrency verified. No duplicate jobs under concurrent access.

---

## Phase 7: User Story 5 — No-Op for Skipped Repos (Priority: P3)

**Goal**: `skipPattern` parameter causes no-op when repoPath matches

**Independent Test**: Call step with skipPattern matching repoPath. Verify no creation, no trigger.

### Implementation for User Story 5

- [x] T028 [US5] Implement skipPattern check at the start of `run()` — if `skipPattern` is non-null and `repoPath` matches, log skip and return immediately (before any rule matching or creation) in `RecursiveCreateAndTriggerStep.java`
- [x] T029 [US5] Write integration test `skipPatternMatchNoOp` — skipPattern matches repoPath, verify no job created, no trigger in `RecursiveCreateAndTriggerStepTest.java`
- [x] T030 [P] [US5] Write integration test `skipPatternNoMatchProceeds` — skipPattern doesn't match, verify normal flow in `RecursiveCreateAndTriggerStepTest.java`
- [x] T031 [P] [US5] Write integration test `noSkipPatternProceeds` — no skipPattern provided, verify normal flow in `RecursiveCreateAndTriggerStepTest.java`
- [x] T032 [US5] Run `mvn clean verify` — all tests pass

**Checkpoint**: Skip pattern works. Step can be used unconditionally in shared pipelines.

---

## Phase 8: Edge Cases

**Purpose**: Cover remaining edge cases from spec

- [x] T033 Write integration test `conflictingItemNotFolder` — create a job (not folder) at a path segment, call step, assert clear error message in `RecursiveCreateAndTriggerStepTest.java`
- [x] T034 Run `mvn clean verify` — all tests pass

**Checkpoint**: All edge cases covered.

---

## Phase 9: Polish — Examples, Deploy & E2E

**Purpose**: Update example Jenkinsfiles, build HPI, deploy to test Jenkins, notify user for E2E testing

- [x] T035 [P] Update `examples/Jenkinsfile` — replace `@NonCPS` functions (`recursiveCreate`, `create`, `resolveTemplate`, `shouldSkipCreate`) with `recursiveCreateAndTrigger` plugin step call, keep SSH URL parsing in pipeline script
- [x] T036 [P] Create `examples/test-recursive-parent.Jenkinsfile` — parent pipeline that parses SSH URL from `env.gitlabTargetRepoSshUrl`, calls `recursiveCreateAndTrigger` with configuration rules, skipPattern, and defaultTemplate
- [x] T037 [P] Create `examples/test-recursive-child.Jenkinsfile` — child template pipeline that calls `setParentEnv()`, `abortOlderBuilds()`, and echoes env vars for verification
- [x] T038 Run final `mvn clean verify` — all tests pass, HPI built at `target/jenkins-mr-automation.hpi`
- [x] T039 Deploy HPI to test Jenkins: `docker cp target/jenkins-mr-automation.hpi jenkins-mr-test:/var/jenkins_home/plugins/jenkins-mr-automation.hpi && docker restart jenkins-mr-test`
- [x] T040 Notify user: plugin deployed to `jenkins-mr-test`, ready for manual E2E testing with the example Jenkinsfiles

---

## Phase 10: E2E Testing on Docker Jenkins

**Purpose**: Create jobs on `jenkins-mr-test` Docker container, trigger them, and verify end-to-end behavior

- [x] T041 Update `examples/test-recursive-parent.Jenkinsfile` with multiple scenario examples: basic creation, deep nesting (3+ levels), skip pattern match, second run (skip-creation path)
- [x] T042 Update `examples/test-recursive-child.Jenkinsfile` to echo all inherited env vars for verification
- [x] T043 Create template job `MergeRequests/Template` on Docker Jenkins using `test-recursive-child.Jenkinsfile`
- [x] T044 Create parent test job `test-recursive-parent` on Docker Jenkins using `test-recursive-parent.Jenkinsfile`
- [x] T045 Trigger parent job and verify E2E results: folder hierarchy created, child job copied, child triggered
- [x] T046 Trigger parent job a second time to verify skip-creation path: no new folders, existing job triggered directly

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1
- **Phase 3 (US1)**: Depends on Phase 2 — **MVP**
- **Phase 4 (US2)**: Depends on Phase 3 (uses same `run()` method)
- **Phase 5 (US3)**: Depends on Phase 3 (extends `run()` with rule matching)
- **Phase 6 (US4)**: Depends on Phase 2 (tests the foundational concurrency logic)
- **Phase 7 (US5)**: Depends on Phase 3 (adds skipPattern to `run()`)
- **Phase 8 (Edge Cases)**: Depends on Phase 2
- **Phase 9 (Polish)**: Depends on all previous phases

### Parallel Opportunities

- T003 + T004 (config.jelly files) can run in parallel
- T021 + T022 + T023 + T024 (US3 tests) — T022, T023, T024 can run in parallel after T021
- T029 + T030 + T031 (US5 tests) — T030, T031 can run in parallel
- T035 + T036 + T037 (example Jenkinsfiles) can all run in parallel
- Phase 6 (US4) and Phase 7 (US5) can run in parallel after Phase 3

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001–T005)
2. Phase 2: Foundational (T006–T011)
3. Phase 3: User Story 1 (T012–T016)
4. **STOP and VALIDATE**: `mvn clean verify` passes, step creates folders + copies template + triggers

### Incremental Delivery

1. Setup + Foundational → skeleton compiles
2. US1 → core flow works (MVP)
3. US2 → idempotent behavior
4. US3 → rule-based template resolution
5. US4 → concurrency verified
6. US5 → skip pattern
7. Edge cases → robustness
8. Polish → deploy to test Jenkins, E2E ready
