# Roadmap: Jenkins MR Automation Plugin

이 문서는 플러그인에 추가될 나머지 pipeline step들을 정리합니다.

## 완료

### Step 1: `setParentEnv`
- **Spec**: [001-jenkins-mr-plugin/spec.md](../001-jenkins-mr-plugin/spec.md)
- 부모 빌드의 환경변수를 현재 빌드에 주입
- 기존 값은 덮어쓰지 않음
- 부모가 없으면 no-op

## 예정

### Step 2: `abortOlderBuilds`
- 같은 Job의 이전 실행 중인 빌드 중, 동일 MR(MR IID 기준) 또는 동일 브랜치 조합(source + target)에 해당하는 빌드를 중단
- 이전 빌드의 MR IID/브랜치 정보는 해당 빌드의 upstream parent 환경변수에서 읽음
- `rawBuild`, `getCauses()`, `getUpstreamRun()` 등 sandbox 제한 API를 plugin 내부에서 처리

### Step 3: `recursiveCreateAndTrigger`
- 입력
  - repo <String> <required>: 입력주는 실제 값
  - configuration : List<Map>
    - pattern : regex 로 표현되는 값
    - target_name : 실제로 만들어질 job 이름. 단, ${REPO}라는 것이 지원되어야 하며, REPO 는 repo 와 일치
    - template_name : copy  될 job 이름
  - defaultTemplate <string> <required>:
  - defaultTargetName <string> : ${REPO}/merge_request 가 기본 값

- 수행:
  - configuraion 을 iteration 하면서 pattern 이 매칭이 되면, target_name 결정. 만약 $REPO 라는 것이 있으면 resolve.
  - target_name 이라는 job 이 없으면 생성. 단, target_name 이 folder 구조라면 폴더까지 생성
  - template_name 을 target_name 으로 복사 후 저장
  - 만약, configuration 이 매칭되지 않으면 defaultTargetName 을 이용, defaultTemplate 이용
  - job 이 있으면 생성 과정은 skip
  - 만들어진 job 을 호출 (wait : false)