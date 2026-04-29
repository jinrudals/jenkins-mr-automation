JAVA_HOME ?= /tools/java/openjdk/17.0.2
export JAVA_HOME
export PATH := $(JAVA_HOME)/bin:$(PATH)

ARTIFACT = target/jenkins-mr-automation.hpi
CONTAINER ?= jenkins-mr-test
PLUGIN_DIR = /var/jenkins_home/plugins

.PHONY: build test verify clean deploy

## Build HPI without running tests
build:
	mvn clean package -DskipTests

## Run unit tests only
test:
	mvn test

## Full build + tests + static analysis (same as CI)
verify:
	mvn clean verify

## Remove build artifacts
clean:
	mvn clean

## Copy HPI to running Jenkins container and restart
deploy: $(ARTIFACT)
	docker cp $(ARTIFACT) $(CONTAINER):$(PLUGIN_DIR)/jenkins-mr-automation.hpi
	docker restart $(CONTAINER)

$(ARTIFACT): build
