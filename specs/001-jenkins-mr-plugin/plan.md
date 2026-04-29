# Implementation Plan: setParentEnv Pipeline Step

**Branch**: `001-jenkins-mr-plugin` | **Date**: 2026-04-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-jenkins-mr-plugin/spec.md`

## Summary

Create a Jenkins plugin that provides a `setParentEnv` pipeline step. This step reads environment variables from the upstream (parent) build and injects them into the current build, replacing the `@NonCPS setParentEnv()` Groovy function that requires Script Security approvals. The plugin is a standard Maven-based Jenkins plugin using the `workflow-step-api` for pipeline step integration.

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: `workflow-step-api` (Jenkins Pipeline Step API)
**Storage**: N/A
**Testing**: JUnit 5 + `JenkinsRule` (jenkins-test-harness)
**Target Platform**: Jenkins 2.479.3+ LTS
**Project Type**: Jenkins plugin (HPI)
**Performance Goals**: Step execution < 100ms (reads parent env and injects)
**Constraints**: Must not require Script Security approvals; must work in both Scripted and Declarative pipelines
**Scale/Scope**: Single pipeline step; plugin skeleton reusable for future steps

## Constitution Check

*GATE: Constitution is not yet defined (template state). No violations to check. Proceeding.*

## Project Structure

### Documentation (this feature)

```text
specs/001-jenkins-mr-plugin/
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
    │       ├── SetParentEnvStep.java          # Step definition + Descriptor
    │       └── InjectEnvVarsAction.java       # EnvironmentContributingAction
    └── resources/
        └── io/jenkins/plugins/mrautomation/
            └── SetParentEnvStep/
                └── config.jelly               # Snippet Generator form (empty, no params)

src/
└── test/
    └── java/
        └── io/jenkins/plugins/mrautomation/
            └── SetParentEnvStepTest.java      # Integration tests with JenkinsRule

pom.xml                                        # Maven plugin POM
```

**Structure Decision**: Single Maven module, standard Jenkins plugin layout. The `io.jenkins.plugins.mrautomation` package is chosen to be reusable when `abortOlderBuilds` and `recursiveCreateAndTrigger` steps are added later.

## Manual E2E Test Jenkinsfiles

플러그인 설치 후 실제 Jenkins에서 수동 검증용. `examples/` 디렉토리에 생성.

### 1. `examples/test-parent.Jenkinsfile` — 부모 Job (자식 호출)

GitLab System Hook이 설정하는 환경변수를 시뮬레이션하고, 자식 Job을 트리거한다.

```groovy
pipeline {
    agent any
    parameters {
        string(name: 'CHILD_JOB', defaultValue: 'test-child', description: 'Child job name to trigger')
    }
    environment {
        gitlabSourceBranch    = 'feature/test-branch'
        gitlabTargetBranch    = 'main'
        gitlabMergeRequestIid = '42'
        gitlabTargetRepoSshUrl  = 'git@gitlab.example.com:group/repo.git'
        gitlabTargetRepoName  = 'repo'
        CUSTOM_VAR            = 'from-parent'
    }
    stages {
        stage('Trigger Child') {
            steps {
                echo "Parent env set. Triggering child job: ${params.CHILD_JOB}"
                build job: "${params.CHILD_JOB}", wait: false
            }
        }
    }
}
```

### 2. `examples/test-child.Jenkinsfile` — 자식 Job (setParentEnv 사용)

부모로부터 트리거되어 `setParentEnv`로 환경변수를 상속받고 출력한다.

```groovy
pipeline {
    agent any
    environment {
        CHILD_OWN_VAR = 'should-not-be-overwritten'
    }
    stages {
        stage('Inherit Parent Env') {
            steps {
                setParentEnv()
            }
        }
        stage('Verify') {
            steps {
                echo "gitlabSourceBranch: ${env.gitlabSourceBranch}"
                echo "gitlabTargetBranch: ${env.gitlabTargetBranch}"
                echo "gitlabMergeRequestIid: ${env.gitlabMergeRequestIid}"
                echo "gitlabTargetRepoSshUrl: ${env.gitlabTargetRepoSshUrl}"
                echo "CUSTOM_VAR: ${env.CUSTOM_VAR}"
                echo "CHILD_OWN_VAR: ${env.CHILD_OWN_VAR}"
            }
        }
    }
}
```

### 검증 포인트

| 확인 항목                  | 기대 결과                                    |
| -------------------------- | -------------------------------------------- |
| `gitlabSourceBranch`       | `feature/test-branch` (부모에서 상속)        |
| `gitlabTargetBranch`       | `main` (부모에서 상속)                       |
| `gitlabMergeRequestIid`    | `42` (부모에서 상속)                         |
| `CUSTOM_VAR`               | `from-parent` (부모에서 상속)                |
| `CHILD_OWN_VAR`            | `should-not-be-overwritten` (덮어쓰기 안 됨) |
| 부모 없이 자식만 단독 실행 | 에러 없이 완료 (no-op)                       |
