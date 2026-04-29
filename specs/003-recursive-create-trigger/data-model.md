# Data Model: `recursiveCreateAndTrigger` Pipeline Step

**Feature**: 003-recursive-create-trigger | **Date**: 2026-04-29

## Entities

### RecursiveCreateAndTriggerStep

The pipeline step class. Extends `Step`.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `repoPath` | `String` | Yes | — | Pre-parsed repository path (e.g., `group/subgroup/repo`) |
| `defaultTemplate` | `String` | Yes | — | Jenkins job path for the fallback template |
| `configuration` | `List<TemplateRule>` | No | `null` | Ordered list of pattern-to-template rules |
| `defaultTargetName` | `String` | No | `${REPO}/merge_request` | Fallback target job name pattern (supports `${REPO}`) |
| `skipPattern` | `String` | No | `null` | Regex; if `repoPath` matches, step is no-op |

**Binding**: `repoPath` and `defaultTemplate` via `@DataBoundConstructor`. Others via `@DataBoundSetter`.

### TemplateRule

Inner static class. Extends `AbstractDescribableImpl<TemplateRule>`. Represents one configuration rule.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `pattern` | `String` | Yes | Regex pattern matched against `repoPath` |
| `finalJobNamePattern` | `String` | Yes | Target job path pattern (supports `${REPO}`) |
| `templateName` | `String` | Yes | Jenkins job path of the template to copy |

**Binding**: All fields via `@DataBoundConstructor`.

**Symbol**: `@Symbol("templateRule")` on descriptor for clean pipeline syntax.

### Execution (inner class)

Extends `SynchronousNonBlockingStepExecution<Void>`. Not a data entity — contains the step logic.

## Relationships

```
RecursiveCreateAndTriggerStep
  ├── 1:N ──► TemplateRule (via configuration list, optional)
  ├── refs ──► Template Job (Jenkins Item, resolved at runtime via templateName or defaultTemplate)
  └── refs ──► Target Job (Jenkins Item, created or found at runtime)

TemplateRule
  └── matched against repoPath via pattern regex
```

## Processing Flow

```
1. Validate repoPath (non-null, non-blank)
2. Check skipPattern → if matches repoPath, return no-op
3. Resolve template + target:
   a. Iterate configuration rules in order
   b. First rule where pattern matches repoPath → use its finalJobNamePattern + templateName
   c. No match → use defaultTargetName + defaultTemplate
4. Resolve ${REPO} in target name → targetJobPath
5. Check if targetJobPath already exists → if yes, skip to step 7
6. Create folder hierarchy + copy template (optimistic, catch IllegalArgumentException)
7. Trigger target job (fire-and-forget via scheduleBuild2)
```

## Variable Resolution

| Variable | Scope | Resolved to |
|----------|-------|-------------|
| `${REPO}` | `finalJobNamePattern`, `defaultTargetName` | Value of `repoPath` parameter |

**Example**: `repoPath=group/repo`, `defaultTargetName=${REPO}/merge_request` → `group/repo/merge_request`

## Jenkins API Types Used

| Jenkins Type | Usage |
|-------------|-------|
| `com.cloudbees.hudson.plugins.folder.Folder` | Folder creation via `createProject()` |
| `hudson.model.TopLevelItem` | Template job reference, copy source |
| `hudson.model.ItemGroup` | Parent container for navigation |
| `jenkins.model.ParameterizedJobMixIn` | `scheduleBuild2()` for triggering |
| `hudson.model.Cause.UpstreamCause` | Links triggered build to parent |
| `hudson.model.Queue.Item` | Return value from `scheduleBuild2()` |
