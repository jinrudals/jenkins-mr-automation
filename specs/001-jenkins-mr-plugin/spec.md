# Feature Specification: Jenkins MR Automation Plugin — `setParentEnv` Step

**Feature Branch**: `001-jenkins-mr-plugin`  
**Created**: 2026-04-29  
**Status**: Draft (Clarified)  
**Input**: User description: "Jenkins plugin to replace Jenkinsfile-based MR automation with native plugin eliminating Script Security approvals"

## Scope

This spec covers **only** the `setParentEnv` pipeline step and the plugin project skeleton. The remaining steps are documented in [roadmap.md](../../roadmap.md).

## User Scenarios & Testing

### User Story 1 - Parent Environment Inheritance via Pipeline Step (Priority: P1)

A pipeline author uses the `setParentEnv` step in a Jenkinsfile. The step reads the parent (upstream) build's environment variables and injects them into the current build's environment. This makes GitLab MR metadata (source branch, target branch, MR IID, repo SSH URL, etc.) available without `@NonCPS` functions or sandbox-restricted API calls.

**Why this priority**: Without parent environment inheritance, child MR jobs cannot access GitLab metadata. This replaces the `setParentEnv()` `@NonCPS` function in `mr.Jenkinsfile`.

**Independent Test**: Create a parent job that sets GitLab environment variables and triggers a child job. In the child job, call `setParentEnv` and verify all parent environment variables are accessible.

**Acceptance Scenarios**:

1. **Given** a child build was triggered by a parent build, **When** the pipeline calls `setParentEnv`, **Then** all environment variables from the parent build are available in the current build's environment.
2. **Given** the parent build has `gitlabSourceBranch`, `gitlabTargetBranch`, `gitlabMergeRequestIid`, and `gitlabTargetRepoSshUrl` set, **When** `setParentEnv` is called in the child, **Then** these variables are accessible via `env.gitlabSourceBranch`, etc.
3. **Given** the child build already has a variable with the same key as a parent variable, **When** `setParentEnv` is called, **Then** the child's existing value is preserved (not overwritten).
4. **Given** the build has no upstream parent, **When** `setParentEnv` is called, **Then** the step completes without error (no-op).

### Edge Cases

- What happens when `setParentEnv` is called in a build with multiple upstream parents? → Use the most recent (last) upstream build.
- What happens when the parent build has been deleted before `setParentEnv` runs? → No-op with a log warning.
- What happens when the parent build's environment contains non-string values? → Skip non-string entries.

## Requirements

### Functional Requirements

- **FR-001**: Plugin MUST provide a `setParentEnv` pipeline step usable in both Scripted and Declarative pipelines.
- **FR-002**: `setParentEnv` MUST read environment variables from the upstream (parent) build that triggered the current build.
- **FR-003**: `setParentEnv` MUST inject parent environment variables into the current build's environment.
- **FR-004**: `setParentEnv` MUST NOT overwrite environment variables that already exist in the current build.
- **FR-005**: `setParentEnv` MUST be a no-op (with no error) when no upstream parent exists.
- **FR-006**: When multiple upstream parents exist, `setParentEnv` MUST use the most recent upstream build.
- **FR-007**: `setParentEnv` MUST operate without requiring Script Security approvals.
- **FR-008**: The plugin project MUST be structured as a standard Jenkins plugin (Maven-based) that can be built and installed as an HPI file.

### Key Entities

- **Parent Environment**: The set of environment variables from the upstream (parent) build that triggered the current build, including GitLab MR metadata.
- **Upstream Build**: A Jenkins build that triggered the current build via the `build` step or similar mechanism. Identified through `Cause.UpstreamCause`.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Zero Script Security approvals required — `setParentEnv` operates without sandbox approvals.
- **SC-002**: A Jenkinsfile using `setParentEnv` can fully replace the `@NonCPS def setParentEnv()` function from `mr.Jenkinsfile`.
- **SC-003**: Parent environment variables are available immediately after `setParentEnv` completes.
- **SC-004**: The plugin builds successfully as an HPI and installs on Jenkins 2.387+.

## Assumptions

- Jenkins 2.387+ (LTS) is the minimum supported version.
- The plugin provides pipeline steps — the Jenkinsfile author is responsible for calling them.
- SSH credentials, GitLab commit status updates, pre-merge checkout, and workflow file parsing are outside this plugin's scope.
