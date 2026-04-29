# Quickstart: Jenkins MR Automation Plugin

**Feature**: 001-jenkins-mr-plugin  
**Date**: 2026-04-29

## Prerequisites

- JDK 17+
- Maven 3.9+
- Jenkins 2.479.3+ (for testing)

## Build

```bash
mvn clean verify
```

This produces `target/jenkins-mr-automation.hpi`.

## Install

1. Jenkins → Manage Jenkins → Plugins → Advanced → Upload Plugin
2. Upload the `.hpi` file
3. Restart Jenkins

## Usage: setParentEnv

In a child Jenkinsfile triggered by a parent job:

```groovy
pipeline {
  agent any
  stages {
    stage('Setup') {
      steps {
        setParentEnv()
        echo "Source branch: ${env.gitlabSourceBranch}"
        echo "Target branch: ${env.gitlabTargetBranch}"
        echo "MR IID: ${env.gitlabMergeRequestIid}"
      }
    }
  }
}
```

### Behavior

- Reads all environment variables from the parent (upstream) build
- Injects them into the current build's environment
- Does NOT overwrite variables already set in the current build
- No-op if there is no parent build (no error)

### Before (requires Script Security approvals)

```groovy
@NonCPS
def setParentEnv() {
  def parentBuild = currentBuild.upstreamBuilds
  if (parentBuild.size() != 0) {
    def pEnv = currentBuild.upstreamBuilds[-1].getRawBuild().getEnvironment()
    pEnv.each {
      if (!env[it.key]) {
        env.setProperty(it.key, it.value)
        env[it.key] = it.value
      }
    }
  }
}
```

### After (zero approvals needed)

```groovy
setParentEnv()
```

## Development

```bash
# Run Jenkins with plugin loaded for manual testing
mvn hpi:run

# Run tests only
mvn test
```
