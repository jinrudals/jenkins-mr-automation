# Quickstart: `abortOlderBuilds` Pipeline Step

**Feature**: 002-abort-older-builds | **Date**: 2026-04-29

## Prerequisites

- Jenkins MR Automation Plugin installed (HPI)
- `setParentEnv` step called before `abortOlderBuilds` in the pipeline

## Usage

### Declarative Pipeline

```groovy
pipeline {
    agent any
    stages {
        stage('Setup') {
            steps {
                setParentEnv()
                abortOlderBuilds()
            }
        }
        // ... remaining stages
    }
}
```

### Scripted Pipeline

```groovy
node {
    setParentEnv()
    abortOlderBuilds()
    // ... remaining steps
}
```

## What It Does

1. Reads `gitlabMergeRequestIid`, `gitlabSourceBranch`, `gitlabTargetBranch` from the current build's environment (injected by `setParentEnv`)
2. Scans all running builds of the same Job with a lower build number
3. For each candidate, reads MR/branch metadata from its upstream parent build's environment
4. Aborts candidates that match by MR IID (primary) or source+target branch (fallback)
5. Logs which builds were aborted and why

## Expected Log Output

```
[abortOlderBuilds] Current build #5: mrIid=42, source=feature/x, target=main
[abortOlderBuilds] Aborting build #3 (same MR IID: 42)
[abortOlderBuilds] Aborting build #4 (same MR IID: 42)
[abortOlderBuilds] Aborted 2 older build(s).
```

When no matches are found:
```
[abortOlderBuilds] Current build #5: mrIid=42, source=feature/x, target=main
[abortOlderBuilds] No older builds to abort.
```

When no upstream parent exists:
```
[abortOlderBuilds] No upstream parent found. No-op.
```

## Build & Install

```bash
mvn verify          # Run tests
mvn package -DskipTests  # Build HPI only
# Install target/jenkins-mr-automation.hpi via Jenkins Plugin Manager → Advanced → Upload
```
