# Jenkins MR Automation Plugin

GitLab MR 파이프라인 자동화를 위한 Jenkins Pipeline Step 플러그인.

기존 Jenkinsfile에서 `@NonCPS` 함수와 sandbox 제한 API로 처리하던 로직을 플러그인 step으로 대체하여, Script Security 승인 없이 안전하게 사용할 수 있습니다.

## Requirements

- Jenkins 2.479.3+ (LTS)
- Java 17

## Pipeline Steps

### `setParentEnv`

부모(upstream) 빌드의 환경변수를 현재 빌드에 주입합니다.

```groovy
setParentEnv()
```

- 부모 빌드의 모든 환경변수를 현재 빌드에 복사
- 이미 존재하는 변수는 덮어쓰지 않음
- 부모가 없으면 no-op (경고 로그만 출력)

### `abortOlderBuilds`

같은 Job에서 동일 MR 또는 동일 브랜치 조합으로 실행 중인 이전 빌드를 중단합니다.

```groovy
abortOlderBuilds()
```

- MR IID 기준 매칭 (우선), 브랜치 조합(source + target) 기준 매칭 (fallback)
- 이전 빌드의 MR/브랜치 정보는 upstream parent 환경변수에서 읽음
- `rawBuild`, `getCauses()` 등 sandbox 제한 API를 플러그인 내부에서 처리

### `recursiveCreateAndTrigger`

리포지토리 경로에 따라 Jenkins Job을 자동 생성(폴더 포함)하고 트리거합니다.

```groovy
recursiveCreateAndTrigger(
    repoPath: 'group/project',
    defaultTemplate: 'MergeRequests/Template',
    defaultTargetName: '${REPO}/merge_request',  // optional
    skipPattern: '.*/experimental/.*',            // optional
    configuration: [                              // optional
        templateRule(
            pattern: '.*abel/ip.*',
            finalJobNamePattern: '${REPO}/ip-mr',
            templateName: 'bos_soc_design/abel/ip/MergeRequestTemplate'
        )
    ]
)
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `repoPath` | ✅ | 리포지토리 경로 (e.g. `group/project`) |
| `defaultTemplate` | ✅ | 기본 복사 대상 Job 이름 |
| `defaultTargetName` | | 생성될 Job 이름 패턴. `${REPO}`가 repoPath로 치환됨. 기본값: `${REPO}/merge_request` |
| `skipPattern` | | 매칭 시 Job 생성/트리거를 건너뛰는 regex |
| `configuration` | | `templateRule` 목록. pattern 매칭 시 해당 template/target 사용 |

## Documentation

- [install.md](install.md) — 빌드 환경 구성 및 HPI 빌드 방법
- [deploy.md](deploy.md) — Jenkins에 플러그인 수동 배포 방법 및 필수 플러그인 목록

## Project Structure

```
src/main/java/io/jenkins/plugins/mrautomation/
├── SetParentEnvStep.java
├── AbortOlderBuildsStep.java
├── RecursiveCreateAndTriggerStep.java
└── InjectEnvVarsAction.java
examples/
├── Jenkinsfile                          # recursiveCreateAndTrigger 사용 예제
├── test-parent.Jenkinsfile              # setParentEnv 테스트 (부모)
├── test-child.Jenkinsfile               # setParentEnv 테스트 (자식)
├── test-abort-parent.Jenkinsfile        # abortOlderBuilds 테스트 (부모)
├── test-abort-child.Jenkinsfile         # abortOlderBuilds 테스트 (자식)
├── test-recursive-parent.Jenkinsfile    # recursiveCreateAndTrigger 테스트 (부모)
└── test-recursive-child.Jenkinsfile     # recursiveCreateAndTrigger 테스트 (자식)
specs/
├── 001-jenkins-mr-plugin/               # setParentEnv spec
├── 002-abort-older-builds/              # abortOlderBuilds spec
└── 003-recursive-create-trigger/        # recursiveCreateAndTrigger spec
```
