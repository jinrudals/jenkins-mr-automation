pipeline {
    agent any
    parameters {
        string(name: 'CHILD_JOB', defaultValue: 'child', description: 'Child job name to trigger')
        string(name: 'gitlabSourceBranch', defaultValue: 'feature/test-branch')
        string(name: 'gitlabTargetBranch', defaultValue: 'main')
        string(name: 'gitlabMergeRequestIid', defaultValue: '42')
        string(name: 'gitlabTargetRepoSshUrl', defaultValue: 'git@gitlab.example.com:group/repo.git')
        string(name: 'gitlabTargetRepoName', defaultValue: 'repo')
        string(name: 'CUSTOM_VAR', defaultValue: 'from-parent')
    }
    stages {
        stage('Trigger Child') {
            steps {
                echo "Parent env set. Triggering child job: ${params.CHILD_JOB}"
                build job: "${params.CHILD_JOB}", wait: false
            }
        }
    }
}
