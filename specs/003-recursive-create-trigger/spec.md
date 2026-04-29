# Feature Specification: `recursiveCreateAndTrigger` Pipeline Step

**Feature Branch**: `003-recursive-create-trigger`  
**Created**: 2026-04-29  
**Status**: Draft  
**Input**: User description: "recursiveCreateAndTrigger pipeline step from README.md"

## Background & Motivation

The current implementation lives in [`examples/Jenkinsfile`](../../examples/Jenkinsfile) as a set of `@NonCPS` functions (`recursiveCreate`, `create`, `resolveTemplate`, `shouldSkipCreate`). These functions call sandbox-restricted APIs — `Jenkins.instance`, `Folder.createProject()`, `currentInstance.copy()`, `item.save()` — which **require Script Security approval** on every Jenkins instance where the Jenkinsfile is used. Each API signature must be individually approved by a Jenkins administrator.

This plugin step replaces those `@NonCPS` functions so that all sandbox-restricted API calls are handled inside the plugin, eliminating the need for Script Security approvals entirely. It also adds race-safe concurrent creation and a configurable rule-based template resolution system.

## User Scenarios & Testing

### User Story 1 - Create Job from Template and Trigger (Priority: P1)

A pipeline author calls `recursiveCreateAndTrigger` with a repo path and a list of pattern-to-template rules. The step matches the repo path against the rules to determine the template, creates the full folder hierarchy and copies the template job, then triggers the newly created job. This is the core end-to-end flow.

**Why this priority**: This is the primary use case — every MR webhook triggers this step to ensure the target job exists and is running.

**Independent Test**: Call `recursiveCreateAndTrigger` with a valid repo path and a single rule. Verify the folder hierarchy is created, the job is copied from the template, and the job is triggered.

**Acceptance Scenarios**:

1. **Given** `repoPath` is `group/subgroup/repo` and a rule matches `group/subgroup/repo`, **When** `recursiveCreateAndTrigger` is called, **Then** folders `group`, `group/subgroup`, `group/subgroup/repo` are created (if missing), the job `group/subgroup/repo/merge_request` is copied from the matched template, and the job is triggered.
2. **Given** the same repo path and rules, **When** the step is called and the matched template job does not exist in Jenkins, **Then** the step fails with a clear error message identifying the missing template.
3. **Given** the repo path is null or empty, **When** the step is called, **Then** the step fails with a clear error message.

---

### User Story 2 - Skip Creation When Job Already Exists (Priority: P1)

When the target job already exists, the step skips creation entirely and immediately triggers the existing job. This ensures idempotent behavior for repeated MR webhook triggers.

**Why this priority**: Most invocations will hit this path since jobs are created once and triggered many times.

**Independent Test**: Pre-create a job at the expected path. Call `recursiveCreateAndTrigger`. Verify no creation occurs and the existing job is triggered.

**Acceptance Scenarios**:

1. **Given** the target job `group/repo/merge_request` already exists, **When** `recursiveCreateAndTrigger` is called, **Then** no folder or job creation occurs and the existing job is triggered.
2. **Given** the target job exists, **When** the step is called, **Then** the step logs that creation was skipped and the job was triggered directly.

---

### User Story 3 - Rule-Based Template Resolution (Priority: P2)

The step accepts an ordered list of rules. Each rule has a `pattern` (regex) and a `template` (Jenkins job path). The step evaluates rules in order and uses the first matching rule's template. This allows different repository paths to use different pipeline templates.

**Why this priority**: Template flexibility is essential for organizations with multiple project types, but the step works with a single rule for simple setups.

**Independent Test**: Provide two rules where the first does not match and the second does. Verify the second rule's template is used.

**Acceptance Scenarios**:

1. **Given** rules `[{pattern: ".*abel/ip.*", finalJobNamePattern: "${REPO}/ip-mr", templateName: "templates/ip-mr"}, {pattern: ".*", finalJobNamePattern: "${REPO}/default-mr", templateName: "templates/default-mr"}]` and repo path `group/abel/ip/core`, **When** the step is called, **Then** the first rule matches and template `templates/ip-mr` is used with target `group/abel/ip/core/ip-mr`.
2. **Given** the same rules and repo path `group/common/lib`, **When** the step is called, **Then** the second rule (catch-all) matches and template `templates/default-mr` is used.
3. **Given** no rule matches the repo path and `defaultTemplate` is `templates/default-mr`, **When** the step is called, **Then** the step uses `defaultTargetName` (`${REPO}/merge_request`) and `defaultTemplate`.

---

### User Story 4 - Race-Safe Concurrent Creation (Priority: P2)

When multiple webhook triggers fire simultaneously for the same repository (e.g., rapid pushes), only one invocation creates the folder hierarchy and job. Other concurrent invocations detect the already-created job and proceed to trigger it without errors or duplicate creation.

**Why this priority**: Concurrent webhooks are common in active repositories and must not cause failures or duplicate jobs.

**Independent Test**: Simulate two concurrent calls for the same repo path. Verify exactly one job is created and both calls successfully trigger it.

**Acceptance Scenarios**:

1. **Given** two concurrent invocations for the same repo path where the job does not yet exist, **When** both call `recursiveCreateAndTrigger`, **Then** exactly one job is created and both invocations trigger it without error.
2. **Given** a concurrent invocation encounters a folder that was just created by another invocation, **When** it attempts to create the same folder, **Then** it detects the existing folder and continues without error.

---

### User Story 5 - No-Op for Non-Matching or Skipped Repos (Priority: P3)

The step supports a `skipPattern` parameter. When the repo path matches this regex, the step completes as a no-op without creation or triggering. This allows the step to be used unconditionally in shared webhook pipelines while excluding certain repositories.

**Why this priority**: Safe no-op behavior enables unconditional use in shared pipeline configurations.

**Independent Test**: Call the step with a `skipPattern` that matches the repo path. Verify it completes without error and performs no action.

**Acceptance Scenarios**:

1. **Given** `skipPattern` is `.*test-only.*` and `repoPath` is `group/test-only/repo`, **When** `recursiveCreateAndTrigger` is called, **Then** the step returns immediately as no-op without creation or triggering.
2. **Given** `skipPattern` is `.*test-only.*` and `repoPath` is `group/production/repo`, **When** the step is called, **Then** the step proceeds normally (skipPattern does not match).
3. **Given** `skipPattern` is not provided, **When** the step is called, **Then** the step proceeds normally (no skip logic applied).

---

### Edge Cases

- What happens when a folder in the hierarchy exists but is not a Folder type (e.g., it's a job with the same name)? → The step fails with a clear error identifying the conflicting item.
- What happens when the Jenkins user running the step lacks permission to create folders or copy jobs? → The step fails with an error indicating insufficient permissions.
- What happens when the SSH URL uses a non-standard format (e.g., `ssh://git@host/group/repo.git`)? → Not applicable — the plugin receives an already-parsed `repoPath`. SSH URL parsing is the pipeline's responsibility.
- What happens when the repo path contains special characters? → The step uses the repo path as-is for folder/job names, consistent with the existing Jenkinsfile behavior.
- What happens when the template job is inside a folder that the current user cannot read? → The step fails with an error indicating the template is not accessible.

## Requirements

### Functional Requirements

- **FR-001**: Plugin MUST provide a `recursiveCreateAndTrigger` pipeline step usable in both Scripted and Declarative pipelines.
- **FR-002**: (Merged into FR-003 parameter list.)
- **FR-003**: The step MUST accept the following parameters:
  - `repoPath` (String, required): Already-parsed repository path.
  - `configuration` (List, optional): Ordered list of rules, each containing `pattern` (String, regex), `finalJobNamePattern` (String, supports `${REPO}` variable), and `templateName` (String, Jenkins job path).
  - `defaultTemplate` (String, required): Template job to use when no configuration rule matches.
  - `defaultTargetName` (String, optional, default: `${REPO}/merge_request`): Target job name pattern when no configuration rule matches. Supports `${REPO}` variable.
  - `skipPattern` (String, optional, regex): If `repoPath` matches this pattern, the step performs no-op (no creation, no trigger) and returns immediately.
- **FR-004**: The step receives `repoPath` directly. No SSH URL parsing is performed by the plugin; the pipeline is responsible for extracting the repo path from the SSH URL before calling the step.
- **FR-004a**: The step MUST resolve `${REPO}` in `finalJobNamePattern` and `defaultTargetName` by replacing it with the provided `repoPath` value.
- **FR-005**: The step MUST first check `skipPattern` — if `repoPath` matches, return no-op. Then evaluate `configuration` rules in order; use the first matching rule's `finalJobNamePattern` and `templateName`. If no rule matches (or `configuration` is empty), fall back to `defaultTargetName` and `defaultTemplate`.
- **FR-006**: The step MUST recursively create the folder hierarchy for the target job path if folders do not exist.
- **FR-007**: The step MUST copy the matched template job to the final position in the folder hierarchy if the job does not exist.
- **FR-008**: The step MUST trigger the target job (whether newly created or pre-existing) with `wait: false` semantics, without passing parameters. The triggered job is expected to use `setParentEnv` to obtain environment variables from its parent build.
- **FR-009**: The step MUST skip creation and proceed directly to triggering when the target job already exists.
- **FR-010**: The step MUST handle concurrent invocations for the same target job without errors or duplicate creation (race-safe), using optimistic creation with exception catching (try-create, catch-if-exists) — no JVM-level locks.
- **FR-011**: The step MUST operate without requiring Script Security approvals — all sandbox-restricted API calls (`Jenkins.instance`, `Folder.createProject()`, `copy()`) are handled inside the plugin.
- **FR-012**: The step MUST log each action taken (folder creation, job copy, trigger, skip) for traceability.
- **FR-013**: The step MUST fail with a clear error when `repoPath` is null, empty, or blank.
- **FR-014**: When no configuration rule matches the repo path, the step MUST fall back to `defaultTargetName` and `defaultTemplate` instead of failing.
- **FR-015**: The step MUST fail with a clear error when the matched template job does not exist in Jenkins.

### Key Entities

- **Repo Path**: The repository identifier provided directly by the pipeline caller (e.g., `group/subgroup/repo`). Used for rule matching and folder hierarchy construction. SSH URL parsing is the caller's responsibility.
- **Rule**: An ordered entry containing a regex `pattern`, a `finalJobNamePattern` (supports `${REPO}` variable), and a `templateName` Jenkins job path. The first matching rule determines the template and target job path.
- **Target Job**: The Jenkins job at the resolved path (after `${REPO}` substitution in `finalJobNamePattern` or `defaultTargetName`) that is created (from template) or triggered.
- **Template Job**: An existing Jenkins job used as the source for copying when creating a new target job.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Zero Script Security approvals required — `recursiveCreateAndTrigger` operates without sandbox approvals.
- **SC-002**: A new repository's first MR webhook automatically creates the full folder hierarchy and job, and triggers the build, without manual Jenkins configuration.
- **SC-003**: Subsequent MR webhooks for the same repository trigger the existing job within 5 seconds (no creation overhead).
- **SC-004**: Concurrent webhook triggers for the same repository do not produce errors or duplicate jobs.
- **SC-005**: Each step invocation logs a clear audit trail of actions taken (created folders, copied job, triggered build, or skipped).

## Clarifications

### Session 2026-04-29

- Q: SSH URL을 환경변수에서 자동으로 읽을지, 명시적 파라미터로 전달할지? → A: 명시적 파라미터로 전달 (Option B).
- Q: 트리거 시 환경변수를 자식 빌드에 전달할지? → A: 파라미터 없이 단순 트리거. 자식이 `setParentEnv`로 부모 환경변수를 가져옴 (Option A).
- Q: 기존 `examples/Jenkinsfile`과의 공존 전략? → A: 기존 Jenkinsfile을 플러그인 step 사용 버전으로 교체하여 예제 업데이트 (Option A).
- Q: 동시성 제어 전략 (race-safe 폴더/잡 생성)? → A: 낙관적 생성 (try-create, catch-if-exists) — lock 없이 생성 시도 후 충돌 시 기존 항목 사용 (Option A).
- Q: SSH URL 파싱을 플러그인이 할지, 파이프라인에 위임할지? → A: 파이프라인에 위임. 플러그인은 이미 파싱된 repo path를 파라미터로 받음.
- Q: rule/파라미터 구조를 roadmap 설계에 맞출 것인가? → A: `finalJobName`→`finalJobNamePattern`(`${REPO}` 치환), `template`→`templateName`, `defaultTemplate`(required)/`defaultTargetName`(기본값 `${REPO}/merge_request`) 추가, step-level `skipPattern`(regex, repoPath 매칭 시 no-op) 추가.

## Assumptions

- The step receives `repoPath` as a pre-parsed parameter. The calling pipeline is responsible for extracting the repo path from the SSH URL (e.g., stripping host prefix and `.git` suffix).
- `finalJobNamePattern` and `defaultTargetName` support `${REPO}` variable substitution, where `${REPO}` is replaced with the provided `repoPath` (e.g., `${REPO}/merge_request` with `repoPath=group/repo` → `group/repo/merge_request`).
- Template jobs are pre-configured in Jenkins by administrators. The step does not create or manage templates.
- Jenkins 2.479.3+ (LTS) is the minimum supported version, consistent with the plugin's baseline.
- The CloudBees Folders plugin is installed (required for `Folder` type and `createProject()`).
- The step triggers the job with `wait: false` — it does not wait for the triggered build to complete.
- Folder and job names derived from the repo path are used as-is without sanitization, consistent with the existing Jenkinsfile behavior.
- Upon completion of this plugin step, the existing `examples/Jenkinsfile` will be updated to use the plugin step instead of the `@NonCPS` functions, serving as the migration example.
