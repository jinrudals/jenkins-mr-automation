import jenkins.model.*
import hudson.model.*
import com.cloudbees.hudson.plugins.folder.Folder
import groovy.transform.Field

/* =========================
 * GLOBAL CONFIG (최상위 관리)
 * ========================= */

// repoPath 기준 skip regex 목록 (repoPath = git@host:<여기>.git 의 <여기>)
@Field
List<String> SKIP_PATTERNS = [
    // 예시: 필요시 추가/수정
    // ".*\\/experimental\\/.*",
    // ".*\\/deprecated\\/.*",
]

// key : regex pattern, value : template job full path
@Field
Map<String, String> TEMPLATE_MAP = [
    ".*abel/ip.*" : "bos_soc_design/abel/ip/MergeRequsetTemplate",
    // 필요시 추가
    // ".*soc/common.*" : "bos_soc_design/common/MergeRequestTemplate",
]

// 기본 템플릿
@Field
String DEFAULT_TEMPLATE = "MergeRequests/Template"


@NonCPS
def resolveTemplate(String jobFullName) {
    def instance = Jenkins.instance

    // first-match 방식
    for (e in TEMPLATE_MAP.entrySet()) {
        def pattern = e.key
        def templatePath = e.value
        if (jobFullName ==~ pattern) {
            println("TEMPLATE :: matched '${pattern}' -> '${templatePath}'")
            return instance.getItemByFullName(templatePath)
        }
    }

    println("TEMPLATE :: default -> '${DEFAULT_TEMPLATE}'")
    return instance.getItemByFullName(DEFAULT_TEMPLATE)
}

@NonCPS
boolean shouldSkipCreate(String jobFullName, String repoPath) {
    def instance = Jenkins.instance

    // 1) 이미 target job이 있으면 skip
    if (instance.getItemByFullName(jobFullName) != null) {
        println("SKIP :: Job already exists: ${jobFullName}")
        return true
    }

    // 2) skip pattern 매칭되면 skip
    for (p in SKIP_PATTERNS) {
        if (p && (repoPath ==~ p)) {
            println("SKIP :: repoPath '${repoPath}' matches skip_pattern '${p}'")
            return true
        }
    }

    return false
}

@NonCPS
def recursiveCreate(String searchname) {
    def instance = Jenkins.instance
    def currentInstance = instance

    def template = resolveTemplate(searchname)
    if (template == null) {
        error("Template job not found. Check TEMPLATE_MAP/DEFAULT_TEMPLATE. searchname=${searchname}")
    }

    if (!currentInstance.getItemByFullName(searchname)) {
        def splitted = searchname.split('/').toList()
        println("PATH :: ${splitted}")

        splitted.eachWithIndex { key, index ->
            def temp = currentInstance.getItem(key)

            if (!temp) {
                if (index != splitted.size() - 1) {
                    println("CREATE :: folder '${key}' in '${currentInstance.fullName ?: '/'}'")
                    currentInstance.createProject(Folder, key)
                    currentInstance = currentInstance.getItem(key)
                    println("ENTER  :: '${currentInstance.fullName}'")
                } else {
                    println("COPY   :: from '${template.fullName}' into '${currentInstance.fullName ?: '/'}' as '${key}'")
                    currentInstance.copy(template, key)
                }
            } else {
                currentInstance = temp
                println("SKIP   :: '${key}' exists, enter '${currentInstance.fullName}'")
            }
        }
    } else {
        println("SKIP :: '${searchname}' already exists")
    }

    def finalJob = Jenkins.instance.getItemByFullName(searchname)
    if (finalJob != null) {
        finalJob.asItem().save()
        println("SAVE :: '${searchname}'")
    }
}

@NonCPS
def makeEnvAvailable() {
    env.getEnvironment().each { k, v -> env.setProperty(k, v) }
}

@NonCPS
def create() {
    println("DEBUG1 :: gitlabTargetRepoSshUrl = ${env.gitlabTargetRepoSshUrl}")

    def pattern = /git@([\w|\.]*):(.*)\.git/
    def matched = (env.gitlabTargetRepoSshUrl =~ pattern)

    if (!matched || matched.size() == 0) {
        error("Cannot parse gitlabTargetRepoSshUrl: ${env.gitlabTargetRepoSshUrl}")
    }

    def repoPath = matched[0][2]               // e.g. group/repo
    def jobname  = "${repoPath}/merge_request" // 최종 job full name
    env.jobname = jobname

    println("DEBUG2 :: repoPath = ${repoPath}")
    println("DEBUG3 :: jobname  = ${jobname}")

    // 조건부 생성 (skip_pattern or already exists)
    if (shouldSkipCreate(jobname, repoPath)) {
        println("INFO :: create() skipped for ${jobname}")
        return
    }

    recursiveCreate(jobname)
}

pipeline {
    agent none

    stages {
        stage('Create Job') {
            steps {
                script {
                    create()

                    // create()가 스킵되었거나 생성 실패면 job이 없을 수 있으니 존재할 때만 트리거
                    def j = Jenkins.instance.getItemByFullName(env.jobname)
                    if (j != null) {
                        build job: "/${env.jobname}", wait: false
                    } else {
                        println("INFO :: Not triggering build because job does not exist: ${env.jobname}")
                    }
                }
            }
        }
    }
}
