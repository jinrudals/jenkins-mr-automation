// Manual E2E: Child job that inherits parent env and aborts older builds.
// Usage: Create this as a Pipeline job named 'test-abort-child'.
// When triggered twice by test-abort-parent, the second build should abort the first.
pipeline {
    agent any
    stages {
        stage('Setup') {
            steps {
                setParentEnv()
                abortOlderBuilds()
            }
        }
        stage('Work') {
            steps {
                echo "MR IID: ${env.gitlabMergeRequestIid}"
                echo "Source: ${env.gitlabSourceBranch}"
                echo "Target: ${env.gitlabTargetBranch}"
                sleep 30  // Hold build running so the second trigger can abort it
            }
        }
    }
}
