# jenkins-job-creation Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-04-29

## Active Technologies
- Java 17 + `workflow-step-api` (already in pom.xml) (002-abort-older-builds)
- Java 17 + `workflow-step-api` (already in pom.xml), `cloudbees-folder` (new runtime dependency) (003-recursive-create-trigger)

- Java 17 + `workflow-step-api` (Jenkins Pipeline Step API) (001-jenkins-mr-plugin)

## Project Structure

```text
src/
tests/
```

## Commands

```bash
export JAVA_HOME=/tools/java/openjdk/17.0.2
export PATH=$JAVA_HOME/bin:$PATH

# Build
mvn clean verify

# Test
mvn test

# Deploy to test Jenkins
docker cp target/jenkins-mr-automation.hpi jenkins-mr-test:/var/jenkins_home/plugins/jenkins-mr-automation.hpi
docker restart jenkins-mr-test
```

## Code Style

Java 17: Follow standard conventions

## Recent Changes
- 003-recursive-create-trigger: Added Java 17 + `workflow-step-api` (already in pom.xml), `cloudbees-folder` (new runtime dependency)
- 002-abort-older-builds: Added Java 17 + `workflow-step-api` (already in pom.xml)

- 001-jenkins-mr-plugin: Added Java 17 + `workflow-step-api` (Jenkins Pipeline Step API)

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
