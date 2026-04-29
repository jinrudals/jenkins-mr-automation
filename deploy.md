# Deploy Guide

Jenkins에 `jenkins-mr-automation.hpi`를 수동으로 배포하는 방법입니다.

## Required Jenkins Plugins

이 플러그인이 동작하려면 아래 플러그인들이 Jenkins에 **먼저 설치**되어 있어야 합니다.

| Plugin | Plugin ID | 용도 |
|--------|-----------|------|
| Pipeline: Step API | `workflow-step-api` | Pipeline step 정의 기반 |
| Pipeline: Groovy | `workflow-cps` | Pipeline CPS 실행 |
| Folders | `cloudbees-folder` | `recursiveCreateAndTrigger`의 폴더 생성 |

> Jenkins 관리 → 플러그인 관리 → "설치된 플러그인" 탭에서 확인할 수 있습니다.

## Deploy via Makefile

```bash
# 기본 컨테이너 이름: jenkins-mr-test
make deploy

# 다른 컨테이너에 배포
make deploy CONTAINER=my-jenkins
```

## Deploy Manually

### 1. Docker 컨테이너에 복사

```bash
docker cp target/jenkins-mr-automation.hpi <CONTAINER>:/var/jenkins_home/plugins/jenkins-mr-automation.hpi
docker restart <CONTAINER>
```

### 2. Jenkins Web UI 업로드

1. Jenkins 관리 → 플러그인 관리 → **고급** 탭
2. "플러그인 올리기" 섹션에서 `jenkins-mr-automation.hpi` 선택
3. **올리기** 클릭
4. Jenkins 재시작

### 3. 직접 파일 복사 (SSH 접근 가능 시)

```bash
scp target/jenkins-mr-automation.hpi <USER>@<JENKINS_HOST>:/var/lib/jenkins/plugins/
# Jenkins 서비스 재시작
ssh <USER>@<JENKINS_HOST> 'sudo systemctl restart jenkins'
```

## Verify

배포 후 확인:

1. Jenkins 관리 → 플러그인 관리 → 설치된 플러그인에서 **Jenkins MR Automation Plugin** 확인
2. Pipeline에서 step 사용 테스트:
   ```groovy
   pipeline {
       agent any
       stages {
           stage('Test') {
               steps {
                   setParentEnv()
               }
           }
       }
   }
   ```
