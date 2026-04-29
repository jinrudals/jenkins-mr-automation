pipeline {
    agent any
    environment {
        CHILD_OWN_VAR = 'should-not-be-overwritten'
    }
    stages {
        stage('Inherit Parent Env') {
            steps {
                setParentEnv()
            }
        }
        stage('Verify') {
            steps {
                echo "gitlabSourceBranch: ${env.gitlabSourceBranch}"
                echo "gitlabTargetBranch: ${env.gitlabTargetBranch}"
                echo "gitlabMergeRequestIid: ${env.gitlabMergeRequestIid}"
                echo "gitlabTargetRepoSshUrl: ${env.gitlabTargetRepoSshUrl}"
                echo "CUSTOM_VAR: ${env.CUSTOM_VAR}"
                echo "CHILD_OWN_VAR: ${env.CHILD_OWN_VAR}"
            }
        }
    }
}
