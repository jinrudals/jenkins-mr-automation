# Research: `recursiveCreateAndTrigger` Pipeline Step

**Feature**: 003-recursive-create-trigger | **Date**: 2026-04-29

## 1. CloudBees Folders Plugin Dependency

**Decision**: Add `cloudbees-folder` as a runtime dependency via Jenkins BOM (no explicit version).

**Rationale**: The BOM (`bom-2.479.x`) already manages compatible versions for the Jenkins 2.479.3 baseline. Using BOM-managed versions avoids version conflicts and simplifies upgrades.

**Alternatives considered**:
- Explicit version pinning (`6.1030.vXXX`) — rejected because BOM already handles this and pinning creates maintenance burden.

**Maven coordinates**:
```xml
<dependency>
    <groupId>org.jenkins-ci.plugins</groupId>
    <artifactId>cloudbees-folder</artifactId>
</dependency>
```

**Gotcha**: artifactId is `cloudbees-folder` (not `cloudbees-folder-plugin`). groupId is `org.jenkins-ci.plugins`.

## 2. Folder Hierarchy Creation API

**Decision**: Use `Folder.createProject(Folder.class, name)` for folder creation and `ItemGroup.getItem(name)` for existence checks. Navigate via `Jenkins.get().getItemByFullName()` for full paths.

**Rationale**: This is the standard CloudBees Folders API. `createProject()` is synchronized per parent, minimizing race windows within a single JVM.

**Key API calls**:
- `Jenkins.get().getItemByFullName(fullPath)` — get any item by slash-separated path
- `parent.getItem(childName)` — get direct child (returns `null` if not found)
- `((Folder) parent).createProject(Folder.class, name)` — create subfolder
- `item instanceof Folder` — type check for folder vs job

**Gotcha**: `getItem()` returns `null` if item doesn't exist OR user lacks READ permission. `getItemByFullName()` behaves the same way.

## 3. Job Copy API

**Decision**: Use `Folder.copy(templateItem, newJobName)` to copy template jobs into target folders.

**Rationale**: `copy()` handles config.xml duplication, descriptor reloading, and event firing. It's the same mechanism Jenkins uses for "Copy from" in the UI.

**Key behavior**:
- Requires `Item.CREATE` on target parent and `Item.EXTENDED_READ` on source
- Calls `Items.verifyItemDoesNotAlreadyExist()` — throws `IllegalArgumentException` if target already exists
- Returns the newly created `TopLevelItem`

**Alternatives considered**:
- Manual config.xml copy + reload — rejected as fragile and bypasses Jenkins event system.

## 4. Optimistic Concurrency Strategy

**Decision**: Check-then-create with `IllegalArgumentException` catch for race conditions. No JVM-level locks.

**Rationale**: `createProject()` and `copy()` are `synchronized` on the parent `ItemGroupMixIn`, so concurrent calls on the same parent serialize naturally. The remaining race window (between `getItem()` check and `createProject()` call) is handled by catching `IllegalArgumentException` and re-fetching the existing item.

**Pattern**:
```java
Item existing = parent.getItem(name);
if (existing instanceof Folder) return (Folder) existing;
if (existing != null) throw new IOException(name + " exists but is not a Folder");
try {
    return parent.createProject(Folder.class, name);
} catch (IllegalArgumentException e) {
    // Race: another thread created it between check and create
    existing = parent.getItem(name);
    if (existing instanceof Folder) return (Folder) existing;
    throw new IOException("Failed to create folder: " + name, e);
}
```

**Same pattern for job copy**: catch `IllegalArgumentException` from `copy()`, then check if job exists and proceed to trigger.

**Exception types**:
| Operation | Already-exists exception |
|-----------|------------------------|
| `Folder.createProject()` | `IllegalArgumentException` |
| `Folder.copy()` | `IllegalArgumentException` |

## 5. Job Triggering (Fire-and-Forget)

**Decision**: Use `ParameterizedJobMixIn.scheduleBuild2(job, 0, new CauseAction(...))` for fire-and-forget triggering.

**Rationale**: Simpler than using the `build` step's internal API. No parameter passing needed (child uses `setParentEnv`). Returns `Queue.Item` or `null` if scheduling refused.

**Key code**:
```java
Job<?,?> job = (Job<?,?>) Jenkins.get().getItemByFullName(targetPath);
Queue.Item qi = ParameterizedJobMixIn.scheduleBuild2(job, 0,
    new CauseAction(new Cause.UpstreamCause(run)));
```

**Gotcha**: Use `Cause.UpstreamCause(run)` to establish the parent-child relationship so `setParentEnv` can find the upstream build.

**Alternatives considered**:
- `Jenkins.get().getQueue().schedule2()` — lower-level, doesn't handle `ParameterizedJob` checks.
- Invoking the `build` step programmatically — too much ceremony for fire-and-forget.

## 6. Step Parameter Design (DataBound Pattern)

**Decision**: `@DataBoundConstructor` for required params (`repoPath`, `defaultTemplate`). `@DataBoundSetter` for optional params (`configuration`, `defaultTargetName`, `skipPattern`). `TemplateRule` as inner static class extending `AbstractDescribableImpl`.

**Rationale**: Follows Jenkins pipeline step conventions. `AbstractDescribableImpl` + `@Extension` descriptor is required for nested objects in `@DataBoundSetter` list parameters.

**TemplateRule structure**:
```java
public static class TemplateRule extends AbstractDescribableImpl<TemplateRule> {
    private final String pattern;
    private final String finalJobNamePattern;
    private final String templateName;

    @DataBoundConstructor
    public TemplateRule(String pattern, String finalJobNamePattern, String templateName) { ... }

    @Extension @Symbol("templateRule")
    public static class DescriptorImpl extends Descriptor<TemplateRule> { }
}
```

**Pipeline syntax**:
```groovy
recursiveCreateAndTrigger repoPath: 'group/repo',
    defaultTemplate: 'templates/default-mr',
    configuration: [
        templateRule(pattern: '.*abel/ip.*', finalJobNamePattern: '${REPO}/ip-mr', templateName: 'templates/ip-mr')
    ]
```

**Gotcha**: The `@Extension` descriptor on `TemplateRule` is mandatory. Without it, Jenkins cannot deserialize the list and throws `ConversionException`.

## 7. `${REPO}` Variable Resolution

**Decision**: Simple `String.replace("${REPO}", repoPath)` on `finalJobNamePattern` and `defaultTargetName`.

**Rationale**: Only one variable (`${REPO}`) needs resolution. No need for a template engine. The `${}` syntax is familiar to Jenkins users from Groovy string interpolation.

**Alternatives considered**:
- Groovy template engine — rejected as overkill and potential security risk.
- Multiple variables (`${GROUP}`, `${PROJECT}`) — rejected per YAGNI; `repoPath` contains the full path.

## 8. Step Execution Class

**Decision**: Use `SynchronousNonBlockingStepExecution<Void>` (same as `AbortOlderBuildsStep` and `SetParentEnvStep`).

**Rationale**: The step performs blocking I/O (folder creation, job copy) but doesn't need to survive Jenkins restart. `SynchronousNonBlockingStepExecution` runs on a non-CPS thread, avoiding CPS serialization issues while keeping the implementation simple.

**Alternatives considered**:
- `SynchronousStepExecution` — blocks a heavyweight thread; not recommended.
- `GeneralNonBlockingStepExecution` — more complex, no benefit for this use case.
