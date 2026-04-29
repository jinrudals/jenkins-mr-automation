# Research: setParentEnv Pipeline Step

**Feature**: 001-jenkins-mr-plugin  
**Date**: 2026-04-29

## R1: Jenkins Pipeline Step API — How to Create a Custom Step

**Decision**: Use `Step` + `SynchronousNonBlockingStepExecution` from `workflow-step-api` plugin.

**Rationale**: `setParentEnv` is a quick, non-blocking operation (reads parent build env vars and injects them). `SynchronousNonBlockingStepExecution` is the recommended approach for steps that do quick work without long-running I/O. It runs off the CPS thread, avoiding serialization issues.

**Alternatives considered**:
- `SynchronousStepExecution` — only for trivial steps, runs on CPS thread. Not recommended by Jenkins docs.
- `AbstractStepImpl` / `AbstractSynchronousNonBlockingStepExecution` — legacy Guice-based API. Deprecated.
- Asynchronous `StepExecution` — overkill for a synchronous operation.

## R2: Accessing Upstream (Parent) Build Environment

**Decision**: Use `Cause.UpstreamCause` from the current build's causes to locate the parent build, then call `getUpstreamRun()` to get the `Run` object, and `getEnvironment(TaskListener.NULL)` to read its environment variables.

**Rationale**: This is exactly what the existing `@NonCPS setParentEnv()` Groovy function does. Moving this logic into a plugin step means it runs in trusted (non-sandboxed) Java code, eliminating Script Security approvals.

**Key API calls**:
- `Run.getCauses()` → list of `Cause`
- `Cause.UpstreamCause.getUpstreamRun()` → parent `Run`
- `Run.getEnvironment(TaskListener)` → `EnvVars` of parent
- `EnvVars.overrideAll()` or manual iteration to inject into current build

**Alternatives considered**:
- `currentBuild.upstreamBuilds` Groovy API — requires sandbox approval for `getRawBuild()`.
- Passing env vars explicitly via `build` step parameters — verbose, fragile, doesn't scale.

## R3: Injecting Environment Variables into Current Build

**Decision**: Use `StepContext.get(EnvVars.class)` to get the current contextual environment, then use `Run.getEnvironment()` combined with `EnvironmentContributingAction` to persist variables for the rest of the build.

**Rationale**: `StepContext.get(EnvVars.class)` gives the contextual env vars. To make injected variables visible to subsequent steps, we need to add an `EnvironmentContributingAction` to the current `Run`. This is the standard Jenkins pattern for steps that modify the build environment.

**Alternatives considered**:
- Directly modifying `EnvVars` from context — changes are not persisted beyond the current step context.
- Using `withEnv` wrapper — requires block-scoped step, changes the usage pattern.

## R4: Plugin Project Structure (Maven)

**Decision**: Standard Jenkins plugin Maven project using `org.jenkins-ci.plugins:plugin` as parent POM.

**Rationale**: This is the standard and only supported way to build Jenkins plugins. The parent POM provides all necessary build infrastructure, dependency management, and HPI packaging.

**Key Maven coordinates**:
- Parent: `org.jenkins-ci.plugins:plugin` (latest stable)
- Dependency: `org.jenkins-ci.plugins.workflow:workflow-step-api`
- Dependency: `org.jenkins-ci.plugins.workflow:workflow-step-api` (tests classifier, for `StepConfigTester`)
- Test: `org.jenkins-ci.plugins.workflow:workflow-cps` (test scope, for `CpsFlowDefinition`)
- Test: `org.jenkins-ci.plugins.workflow:workflow-job` (test scope, for `WorkflowJob`)
- Test: `org.jenkins-ci.plugins.workflow:workflow-basic-steps` (test scope, for `build` step in tests)

## R5: Testing Strategy

**Decision**: Use `JenkinsRule` with `WorkflowJob` and `CpsFlowDefinition` for integration testing.

**Rationale**: Jenkins plugin testing uses `JenkinsRule` which spins up a real Jenkins instance. For pipeline step testing, we create a `WorkflowJob`, set a `CpsFlowDefinition` (inline pipeline script), and run it. This validates the step works end-to-end in a real pipeline.

**Test scenarios**:
1. Parent job triggers child job → child calls `setParentEnv` → verify parent env vars are available.
2. No parent build → `setParentEnv` is no-op.
3. Child already has a variable → not overwritten by parent.

**Alternatives considered**:
- `RealJenkinsRule` — heavier, runs Jenkins in a separate JVM. Overkill for this.
- Unit testing `StepExecution` directly — misses integration with pipeline engine.
