/* =========================
 * E2E Test: Parent Pipeline for recursiveCreateAndTrigger
 *
 * Tests multiple scenarios in sequence:
 *   1. Basic creation — new repo, default template
 *   2. Deep nesting — 4-level folder hierarchy
 *   3. Already exists — second call skips creation
 *   4. Custom defaultTargetName — ${REPO}/custom-mr
 *   5. Configuration rule match — first-match wins
 *
 * Setup:
 *   1. Create "MergeRequests/Template" job using test-recursive-child.Jenkinsfile
 *   2. Create this as a Pipeline job (e.g., "test-recursive-parent")
 *   3. Run — all scenarios execute in sequence
 * ========================= */

pipeline {
    agent any

    stages {
        stage('Scenario 1: Basic Creation') {
            steps {
                echo '=== Scenario 1: Basic creation with default template ==='
                recursiveCreateAndTrigger(
                    repoPath: 'testgroup/testrepo',
                    defaultTemplate: 'MergeRequests/Template'
                )
                echo 'Expected: testgroup/testrepo/merge_request created and triggered'
            }
        }

        stage('Scenario 2: Deep Nesting') {
            steps {
                echo '=== Scenario 2: Deep folder hierarchy (4 levels) ==='
                recursiveCreateAndTrigger(
                    repoPath: 'org/division/team/project',
                    defaultTemplate: 'MergeRequests/Template'
                )
                echo 'Expected: org/division/team/project/merge_request created and triggered'
            }
        }

        stage('Scenario 3: Already Exists (skip creation)') {
            steps {
                echo '=== Scenario 3: Same repo again — should skip creation ==='
                recursiveCreateAndTrigger(
                    repoPath: 'testgroup/testrepo',
                    defaultTemplate: 'MergeRequests/Template'
                )
                echo 'Expected: SKIP creation, trigger existing testgroup/testrepo/merge_request'
            }
        }

        stage('Scenario 4: Custom Target Name') {
            steps {
                echo '=== Scenario 4: Custom defaultTargetName ==='
                recursiveCreateAndTrigger(
                    repoPath: 'custom/repo',
                    defaultTemplate: 'MergeRequests/Template',
                    defaultTargetName: '${REPO}/code-review'
                )
                echo 'Expected: custom/repo/code-review created and triggered'
            }
        }

        stage('Scenario 5: Configuration Rule Match') {
            steps {
                echo '=== Scenario 5: First-match rule wins ==='
                recursiveCreateAndTrigger(
                    repoPath: 'special/abel/ip/core',
                    defaultTemplate: 'MergeRequests/Template',
                    configuration: [
                        templateRule(
                            pattern: '.*abel/ip.*',
                            finalJobNamePattern: '${REPO}/ip-mr',
                            templateName: 'MergeRequests/Template'
                        ),
                        templateRule(
                            pattern: '.*',
                            finalJobNamePattern: '${REPO}/default-mr',
                            templateName: 'MergeRequests/Template'
                        )
                    ]
                )
                echo 'Expected: special/abel/ip/core/ip-mr created (first rule matched)'
            }
        }
    }

    post {
        always {
            echo '=== E2E Test Complete ==='
            echo 'Check Jenkins UI to verify all folder hierarchies and jobs were created.'
        }
    }
}
