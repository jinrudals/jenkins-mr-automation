# Quickstart: `recursiveCreateAndTrigger` Pipeline Step

## Minimal Usage (defaults only)

```groovy
recursiveCreateAndTrigger repoPath: 'group/repo',
    defaultTemplate: 'MergeRequests/Template'
```

This creates `group/repo/merge_request` (from `defaultTargetName: '${REPO}/merge_request'`) by copying `MergeRequests/Template`, then triggers it.

## With Configuration Rules

```groovy
recursiveCreateAndTrigger repoPath: env.REPO_PATH,
    defaultTemplate: 'MergeRequests/Template',
    configuration: [
        templateRule(
            pattern: '.*abel/ip.*',
            finalJobNamePattern: '${REPO}/ip-mr',
            templateName: 'bos_soc_design/abel/ip/MergeRequestTemplate'
        ),
        templateRule(
            pattern: '.*soc/common.*',
            finalJobNamePattern: '${REPO}/common-mr',
            templateName: 'bos_soc_design/common/MergeRequestTemplate'
        )
    ]
```

Rules are evaluated in order. First match wins. If no rule matches, `defaultTemplate` + `defaultTargetName` are used.

## With Skip Pattern

```groovy
recursiveCreateAndTrigger repoPath: env.REPO_PATH,
    defaultTemplate: 'MergeRequests/Template',
    skipPattern: '.*/(experimental|deprecated)/.*'
```

If `repoPath` matches `skipPattern`, the step returns immediately as no-op.

## Full Example (replacing examples/Jenkinsfile)

```groovy
pipeline {
    agent none
    stages {
        stage('Create and Trigger') {
            steps {
                script {
                    def sshUrl = env.gitlabTargetRepoSshUrl
                    def matcher = (sshUrl =~ /git@[\w.]+:(.*)\.git/)
                    def repoPath = matcher ? matcher[0][1] : error("Cannot parse SSH URL: ${sshUrl}")

                    recursiveCreateAndTrigger repoPath: repoPath,
                        defaultTemplate: 'MergeRequests/Template',
                        skipPattern: '.*/(experimental|deprecated)/.*',
                        configuration: [
                            templateRule(
                                pattern: '.*abel/ip.*',
                                finalJobNamePattern: '${REPO}/ip-mr',
                                templateName: 'bos_soc_design/abel/ip/MergeRequestTemplate'
                            )
                        ]
                }
            }
        }
    }
}
```

## Prerequisites

- Jenkins 2.479.3+ LTS
- CloudBees Folders plugin installed
- Template jobs pre-configured in Jenkins by an administrator
- Plugin `jenkins-mr-automation` installed (provides `recursiveCreateAndTrigger`, `setParentEnv`, `abortOlderBuilds`)

## Parameter Reference

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `repoPath` | String | Yes | — | Pre-parsed repo path (e.g., `group/repo`) |
| `defaultTemplate` | String | Yes | — | Fallback template job path |
| `defaultTargetName` | String | No | `${REPO}/merge_request` | Fallback target job name pattern |
| `configuration` | List | No | `[]` | Ordered list of `templateRule(...)` entries |
| `skipPattern` | String | No | `null` | Regex; matching repoPath → no-op |

### templateRule Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `pattern` | String | Yes | Regex matched against `repoPath` |
| `finalJobNamePattern` | String | Yes | Target job path pattern (`${REPO}` resolved) |
| `templateName` | String | Yes | Template job path to copy from |
