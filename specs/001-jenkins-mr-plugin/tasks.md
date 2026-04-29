# Tasks: setParentEnv Pipeline Step Plugin

**Input**: Design documents from `/specs/001-jenkins-mr-plugin/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**Tests**: Included — integration tests with `JenkinsRule` are part of the plugin's core validation (per research.md R5).

**Organization**: Single user story (US1: Parent Environment Inheritance). Tasks organized by phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[US1]**: User Story 1 — Parent Environment Inheritance via Pipeline Step

---

## Phase 1: Setup (Project Initialization)

**Purpose**: Create Maven plugin project skeleton and build infrastructure

- [x] T001 Create `pom.xml` with `org.jenkins-ci.plugins:plugin` parent POM, Java 17, `workflow-step-api` dependency, and test dependencies (`workflow-cps`, `workflow-job`, `workflow-basic-steps`)
- [x] T002 Create directory structure: `src/main/java/io/jenkins/plugins/mrautomation/`, `src/main/resources/io/jenkins/plugins/mrautomation/SetParentEnvStep/`, `src/test/java/io/jenkins/plugins/mrautomation/`

**Checkpoint**: `mvn validate` passes — project structure and POM are valid

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Snippet Generator configuration (required before step implementation can be tested in pipelines)

- [x] T003 Create empty `src/main/resources/io/jenkins/plugins/mrautomation/SetParentEnvStep/config.jelly` for Snippet Generator support

**Checkpoint**: Foundation ready — user story implementation can begin

---

## Phase 3: User Story 1 — Parent Environment Inheritance (Priority: P1) 🎯 MVP

**Goal**: `setParentEnv` pipeline step reads parent build env vars and injects them into the current build, replacing the `@NonCPS setParentEnv()` function.

**Independent Test**: Parent job sets GitLab env vars → triggers child job → child calls `setParentEnv` → verify parent env vars are accessible and child's own vars are not overwritten.

### Implementation for User Story 1

- [x] T004 [P] [US1] Implement `InjectEnvVarsAction` (`EnvironmentContributingAction`) in `src/main/java/io/jenkins/plugins/mrautomation/InjectEnvVarsAction.java`
- [x] T005 [P] [US1] Implement `SetParentEnvStep` (Step + Descriptor + `SynchronousNonBlockingStepExecution`) in `src/main/java/io/jenkins/plugins/mrautomation/SetParentEnvStep.java`
- [x] T006 [US1] Implement `SetParentEnvStepTest` integration tests with `JenkinsRule` in `src/test/java/io/jenkins/plugins/mrautomation/SetParentEnvStepTest.java` — cover: parent env inherited, no-op without parent, child vars not overwritten

**Checkpoint**: `mvn clean verify` passes — plugin builds as HPI, all tests green

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Manual E2E test Jenkinsfiles and validation

- [x] T007 [P] Create `examples/test-parent.Jenkinsfile` (parent job simulating GitLab env vars and triggering child)
- [x] T008 [P] Create `examples/test-child.Jenkinsfile` (child job calling `setParentEnv` and verifying inherited vars)
- [x] T009 Run `mvn clean verify` and validate HPI artifact is produced at `target/jenkins-mr-automation.hpi`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (directory structure must exist)
- **User Story 1 (Phase 3)**: Depends on Phase 2
- **Polish (Phase 4)**: Depends on Phase 3

### Within User Story 1

- T004 and T005 can run in parallel (different files)
- T006 depends on T004 and T005 (tests exercise both classes)

### Parallel Opportunities

```text
# Phase 3 — parallel implementation:
T004: InjectEnvVarsAction.java
T005: SetParentEnvStep.java

# Phase 4 — parallel Jenkinsfiles:
T007: examples/test-parent.Jenkinsfile
T008: examples/test-child.Jenkinsfile
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: Foundational (T003)
3. Complete Phase 3: User Story 1 (T004–T006)
4. **STOP and VALIDATE**: `mvn clean verify` — all tests pass, HPI produced
5. Deploy HPI to Jenkins and test with Phase 4 Jenkinsfiles

### Incremental Delivery

1. Setup + Foundational → Project compiles
2. User Story 1 → `setParentEnv` works end-to-end → Deploy (MVP!)
3. Polish → Manual E2E validation Jenkinsfiles added

---

## Notes

- [P] tasks = different files, no dependencies
- [US1] = User Story 1: Parent Environment Inheritance
- Only one user story in scope — remaining steps (`abortOlderBuilds`, `recursiveCreateAndTrigger`) are in README.md
- Commit after each task or logical group
- Plugin must work on Jenkins 2.479.3+ LTS
