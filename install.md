# Install Guide

## Prerequisites

| Tool | Version | Note |
|------|---------|------|
| Java (JDK) | 17 | OpenJDK 17 권장 |
| Maven | 3.8+ | `mvn -version` 으로 확인 |
| Docker | any | deploy 시 필요 (Jenkins 컨테이너 접근용) |

## Environment Setup

```bash
# JAVA_HOME 설정 (사내 환경 기준)
export JAVA_HOME=/tools/java/openjdk/17.0.2
export PATH=$JAVA_HOME/bin:$PATH

# 확인
java -version   # openjdk 17.x 출력되어야 함
mvn -version    # Maven 3.8+ & Java 17 확인
```

> 다른 경로에 JDK 17이 설치되어 있다면 `JAVA_HOME`을 해당 경로로 변경하세요.

## Build

```bash
# HPI 빌드 (테스트 제외)
make build

# 테스트만 실행
make test

# 전체 빌드 + 테스트 + 정적 분석
make verify

# 빌드 산출물 삭제
make clean
```

빌드 완료 후 `target/jenkins-mr-automation.hpi` 파일이 생성됩니다.

## Makefile 없이 직접 실행

```bash
# 빌드
mvn clean package -DskipTests

# 테스트
mvn test

# 전체 검증
mvn clean verify
```

## Troubleshooting

- **`java: error: release version 17 is not supported`** → `JAVA_HOME`이 JDK 17을 가리키는지 확인
- **Maven dependency 다운로드 실패** → `pom.xml`의 `repo.jenkins-ci.org` 저장소 접근 가능한지 네트워크 확인
