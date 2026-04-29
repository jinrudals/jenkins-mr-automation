// Manual E2E: Parent job that triggers child twice to test abortOlderBuilds.
// Usage: Create this as a Pipeline job, then run it. It triggers CHILD_JOB twice
// with wait:false so both child builds run concurrently. The second child build
// should abort the first via abortOlderBuilds().
pipeline {
    agent any
    parameters {
        string(name: 'CHILD_JOB', defaultValue: 'test-abort-child', description: 'Child job to trigger')
        string(name: 'gitlabSourceBranch', defaultValue: 'feature/abort-test')
        string(name: 'gitlabTargetBranch', defaultValue: 'main')
        string(name: 'gitlabMergeRequestIid', defaultValue: '42')
    }
    stages {
        stage('Trigger Child Twice') {
            steps {
                echo "Triggering ${params.CHILD_JOB} twice with MR IID=${params.gitlabMergeRequestIid}"
                build job: "${params.CHILD_JOB}", wait: false
                sleep 3
                build job: "${params.CHILD_JOB}", wait: false
            }
        }
    }
}
