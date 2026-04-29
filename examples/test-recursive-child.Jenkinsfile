/* =========================
 * E2E Test: Child Template Pipeline
 *
 * Setup:
 *   1. Create this as a Pipeline job at "MergeRequests/Template"
 *   2. This job is copied by recursiveCreateAndTrigger when a new repo is detected
 *   3. The copied job inherits this pipeline definition
 * ========================= */

pipeline {
    agent any

    stages {
        stage('Inherit Env') {
            steps {
                setParentEnv()
                abortOlderBuilds()
            }
        }
        stage('Verify') {
            steps {
                echo "gitlabTargetRepoSshUrl = ${env.gitlabTargetRepoSshUrl ?: 'N/A'}"
                echo "gitlabSourceBranch = ${env.gitlabSourceBranch ?: 'N/A'}"
                echo "gitlabTargetBranch = ${env.gitlabTargetBranch ?: 'N/A'}"
                echo "gitlabMergeRequestIid = ${env.gitlabMergeRequestIid ?: 'N/A'}"
                echo "Child job running successfully!"
            }
        }
    }
}
