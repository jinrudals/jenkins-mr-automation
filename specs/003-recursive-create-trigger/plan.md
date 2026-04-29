# Implementation Plan: `recursiveCreateAndTrigger` Pipeline Step

**Branch**: `003-recursive-create-trigger` | **Date**: 2026-04-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-recursive-create-trigger/spec.md`

## Summary

Add a `recursiveCreateAndTrigger` pipeline step to the existing Jenkins MR Automation Plugin. The step receives a pre-parsed `repoPath`, matches it against an ordered list of configuration rules (or falls back to defaults) to determine the target job path and template, recursively creates the folder hierarchy, copies the template job, and triggers it. All sandbox-restricted API calls (`Jenkins.instance`, `Folder.createProject()`, `copy()`, `item.save()`) are encapsulated inside the plugin, eliminating Script Security approvals. Race-safe via optimistic creation with exception catching. Replaces the `@NonCPS` functions in `examples/Jenkinsfile`.

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: `workflow-step-api` (already in pom.xml), `cloudbees-folder` (new runtime dependency)
**Storage**: N/A
**Testing**: JUnit + `JenkinsRule` (jenkins-test-harness, already configured)
**Target Platform**: Jenkins 2.479.3 LTS (as declared in pom.xml)
**Project Type**: Jenkins plugin (HPI) — adding a new step to existing plugin
**Performance Goals**: Step execution < 5s for first-time creation (folder hierarchy + copy); < 1s for existing job trigger
**Constraints**: No Script Security approvals; no JVM-level locks for concurrency; CloudBees Folders plugin required at runtime
**Scale/Scope**: Single pipeline step added to existing plugin; reuses existing package and build infrastructure

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution is in template state (no project-specific principles defined). No gates to evaluate. Proceeding with standard Jenkins plugin conventions established by prior features (001, 002).

| Principle | Status | Notes |
|-----------|--------|-------|
| Jenkins Plugin Conventions | ✅ PASS | Same plugin, same POM, `@Extension` step registration |
| Pipeline Step Contract | ✅ PASS | `@DataBoundConstructor` + `@DataBoundSetter`, clear error messages, no-op for skipPattern |
| Test-First | ✅ PASS | JenkinsRule integration tests planned for all user stories |
| Sandbox Safety | ✅ PASS | All restricted APIs (`Jenkins.instance`, `Folder.createProject()`, `copy()`, `save()`) inside plugin code |
| Simplicity & YAGNI | ✅ PASS | Single Step class + inner Execution + DescriptorImpl + one Rule POJO. No abstraction layers |
| Technology Constraints | ✅ PASS | Java 17, Jenkins 2.479.3, BOM 2.479.x, one new dependency (`cloudbees-folder`) |

No violations. No Complexity Tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/003-recursive-create-trigger/
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
    ├── java/
    │   └── io/jenkins/plugins/mrautomation/
    │       ├── SetParentEnvStep.java                    # (existing)
    │       ├── InjectEnvVarsAction.java                 # (existing)
    │       ├── AbortOlderBuildsStep.java                # (existing)
    │       ├── RecursiveCreateAndTriggerStep.java        # NEW — Step + @DataBoundConstructor/Setter + Descriptor
    │       └── RecursiveCreateAndTriggerStep.java        # NEW — inner Execution class + TemplateRule POJO
    └── resources/
        └── io/jenkins/plugins/mrautomation/
            └── RecursiveCreateAndTriggerStep/
                └── config.jelly                          # NEW — step configuration UI

src/
└── test/
    └── java/
        └── io/jenkins/plugins/mrautomation/
            └── RecursiveCreateAndTriggerStepTest.java    # NEW — JenkinsRule integration tests

examples/
└── Jenkinsfile                                           # UPDATED — replace @NonCPS functions with plugin step
```

**Structure Decision**: Single new Java file (`RecursiveCreateAndTriggerStep.java`) containing the Step class, inner `TemplateRule` POJO (for `configuration` list entries), inner `Execution` class, and `@Extension DescriptorImpl`. Follows the same pattern as `AbortOlderBuildsStep.java` and `SetParentEnvStep.java`. The `TemplateRule` is an inner static class (not a separate file) since it's only used by this step.

## Test Strategy

### Integration Tests (`RecursiveCreateAndTriggerStepTest.java` with JenkinsRule)

| Test | User Story | What it validates |
|------|-----------|-------------------|
| `createJobFromTemplateAndTrigger` | US1 | Full flow: folder hierarchy creation + template copy + trigger |
| `failsWhenRepoPathEmpty` | US1 | Error on null/empty repoPath |
| `failsWhenTemplateNotFound` | US1 | Error when template job doesn't exist |
| `skipCreationWhenJobExists` | US2 | Existing job → skip creation, trigger directly |
| `ruleBasedTemplateFirstMatch` | US3 | First matching rule's template used |
| `ruleBasedTemplateFallthrough` | US3 | Second rule matches when first doesn't |
| `defaultFallbackWhenNoRuleMatches` | US3 | No rule match → defaultTemplate + defaultTargetName |
| `concurrentCreationRaceSafe` | US4 | Two concurrent calls → one creates, both trigger |
| `skipPatternMatchNoOp` | US5 | skipPattern match → no-op |
| `skipPatternNoMatchProceeds` | US5 | skipPattern doesn't match → normal flow |
| `noSkipPatternProceeds` | US5 | No skipPattern → normal flow |
| `conflictingItemNotFolder` | Edge | Non-folder item at path → clear error |
| `repoVariableResolution` | FR-004a | `${REPO}` replaced in finalJobNamePattern and defaultTargetName |

### E2E Test Scenario

**Full pipeline flow**: Parent pipeline calls `recursiveCreateAndTrigger` → child job created from template → child triggered → child uses `setParentEnv` to inherit env. Validated via JenkinsRule with WorkflowJob + CpsFlowDefinition.

### Manual E2E Jenkinsfiles (final implementation step)

After all code and unit/integration tests pass, create example Jenkinsfiles in `examples/` for manual E2E testing on a real Jenkins instance:

| File | Purpose |
|------|---------|
| `examples/Jenkinsfile` | **Updated** — replace `@NonCPS` functions with `recursiveCreateAndTrigger` plugin step |
| `examples/test-recursive-parent.Jenkinsfile` | Parent pipeline: parses SSH URL → calls `recursiveCreateAndTrigger` with configuration rules and skipPattern |
| `examples/test-recursive-child.Jenkinsfile` | Child template pipeline: calls `setParentEnv()` + `abortOlderBuilds()` + echo env vars for verification |

These files allow the user to:
1. Set up the parent Jenkinsfile as a webhook-triggered job
2. Set up the child Jenkinsfile as the template job (e.g., `MergeRequests/Template`)
3. Trigger the parent manually or via GitLab webhook
4. Verify folder hierarchy creation, template copy, job trigger, and env inheritance end-to-end

### Deploy & Reload (final step)

After `mvn clean verify` succeeds and all tests pass:

1. Copy the built HPI to the `jenkins-mr-test` Docker container's plugin directory
2. Reload Jenkins (safe restart or plugin reload via CLI)
3. Notify the user to perform manual E2E testing

```bash
# Copy HPI to container
docker cp target/jenkins-mr-automation.hpi jenkins-mr-test:/var/jenkins_home/plugins/jenkins-mr-automation.hpi

# Restart Jenkins to load the new plugin
docker exec jenkins-mr-test jenkins-plugin-cli --war /usr/share/jenkins/jenkins.war --plugin-file /var/jenkins_home/plugins/jenkins-mr-automation.hpi || true
docker restart jenkins-mr-test
```
