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
- 입력:
  - `gitlabTargetRepoSshUrl` (String) — GitLab repo SSH URL
  - 순서 있는 규칙 리스트 (List). 각 항목:
    - `pattern` (String) — repo path에 대한 regex 매칭용
    - `final_job_name` (String) — 생성할 최종 Job 이름
    - `template` (String) — 복사할 템플릿 Job 경로
- 동작:
  - SSH URL에서 repo path 추출 (예: `git@host:group/repo.git` → `group/repo`)
  - 규칙 리스트를 순서대로 평가, 첫 번째 매칭 사용
  - 폴더 계층 재귀 생성 → 템플릿 복사 → Job 트리거
  - Job이 이미 존재하면 생성 건너뛰고 바로 트리거
  - 동시 호출 시 중복 생성 방지 (race-safe)
- `Jenkins.instance`, `Folder.createProject()`, `copy()` 등 sandbox 제한 API를 plugin 내부에서 처리
