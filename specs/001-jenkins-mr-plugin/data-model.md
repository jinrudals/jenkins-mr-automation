# Data Model: setParentEnv Pipeline Step

**Feature**: 001-jenkins-mr-plugin  
**Date**: 2026-04-29

## Entities

### SetParentEnvStep

The pipeline step definition. No user-configurable parameters — it reads the parent build automatically.

- **Fields**: None (parameterless step)
- **Descriptor symbol**: `setParentEnv`
- **Required context**: `Run`, `TaskListener`

### SetParentEnvStep.Execution

The step execution logic.

- **Input**: Current `Run` (from `StepContext`)
- **Process**:
  1. Get `Run.getCauses()` from current build
  2. Filter for `Cause.UpstreamCause` instances
  3. If multiple, use the last (most recent) one
  4. Call `getUpstreamRun()` to get parent `Run`
  5. Call `parent.getEnvironment(TaskListener.NULL)` to get parent `EnvVars`
  6. For each parent env var: if key does NOT exist in current build's env, inject it
  7. Injection via `EnvironmentContributingAction` added to current `Run`
- **Output**: `Void` (side effect: env vars injected)

### InjectEnvVarsAction

An `InvisibleAction` implementing `EnvironmentContributingAction` that holds the map of injected variables and contributes them to subsequent steps.

- **Fields**:
  - `envVars` (Map<String, String>) — the variables to inject
- **Behavior**: `buildEnvironment(Run, EnvVars)` adds all entries from `envVars` to the build environment

## State Transitions

```
setParentEnv called
  → Has upstream cause?
    → No: no-op, return
    → Yes: get parent Run
      → Parent Run exists?
        → No: log warning, return
        → Yes: read parent EnvVars
          → For each var not in current env: add to InjectEnvVarsAction
          → Attach action to current Run
          → return
```
