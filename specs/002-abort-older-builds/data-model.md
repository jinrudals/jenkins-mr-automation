# Data Model: `abortOlderBuilds` Pipeline Step

**Feature**: 002-abort-older-builds | **Date**: 2026-04-29

## Entities

### AbortOlderBuildsStep

The pipeline step definition. No parameters — the step reads all needed data from the build context.

| Field | Type | Description |
|-------|------|-------------|
| (none) | — | Step has no configurable parameters |

### Execution Context (runtime, not persisted)

Data gathered during step execution to perform matching and abort.

| Field | Source | Description |
|-------|--------|-------------|
| `myBuildNumber` | `Run.getNumber()` | Current build number — used to filter only older builds |
| `myMrIid` | `EnvVars.get("gitlabMergeRequestIid")` | Current build's MR IID (from `setParentEnv`) |
| `mySourceBranch` | `EnvVars.get("gitlabSourceBranch")` | Current build's source branch (from `setParentEnv`) |
| `myTargetBranch` | `EnvVars.get("gitlabTargetBranch")` | Current build's target branch (from `setParentEnv`) |
| `job` | `Run.getParent()` | The Job containing all builds to scan |

### Candidate Build (runtime, not persisted)

Each running build of the same Job with a lower build number.

| Field | Source | Description |
|-------|--------|-------------|
| `buildNumber` | `Run.getNumber()` | Candidate's build number |
| `bMrIid` | Upstream parent env → `gitlabMergeRequestIid` | Candidate's MR IID |
| `bSourceBranch` | Upstream parent env → `gitlabSourceBranch` | Candidate's source branch |
| `bTargetBranch` | Upstream parent env → `gitlabTargetBranch` | Candidate's target branch |

## Matching Logic

```
if myMrIid is non-empty AND bMrIid == myMrIid → MATCH (abort)
else if myMrIid is empty AND mySourceBranch is non-empty
     AND bSourceBranch == mySourceBranch AND bTargetBranch == myTargetBranch → MATCH (abort)
else → NO MATCH (skip)
```

## State Transitions

This step does not manage persistent state. It performs a one-shot scan-and-abort:

```
START → Read current env → Scan running builds → For each candidate:
  → Read upstream parent env → Match? → Yes: Abort → No: Skip
→ Log results → END
```

Aborted builds transition from `BUILDING` to `ABORTED` (managed by Jenkins core, not by this plugin).
