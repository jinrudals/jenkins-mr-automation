package io.jenkins.plugins.mrautomation;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class RecursiveCreateAndTriggerStepTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    // --- US1: Create Job from Template and Trigger ---

    @Test
    public void createJobFromTemplateAndTrigger() throws Exception {
        // Create template job at root
        j.createFreeStyleProject("template-mr");

        WorkflowJob parent = j.createProject(WorkflowJob.class, "parent-job");
        parent.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'group/subgroup/repo', defaultTemplate: 'template-mr'",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(parent.scheduleBuild2(0));
        String log = run.getLog();

        // Verify folder hierarchy created
        assertNotNull("group folder should exist", j.jenkins.getItemByFullName("group"));
        assertNotNull("group/subgroup folder should exist", j.jenkins.getItemByFullName("group/subgroup"));
        assertNotNull("group/subgroup/repo folder should exist", j.jenkins.getItemByFullName("group/subgroup/repo"));

        // Verify job copied
        assertNotNull("target job should exist", j.jenkins.getItemByFullName("group/subgroup/repo/merge_request"));

        // Verify trigger logged
        assertTrue("should log trigger", log.contains("TRIGGER job: group/subgroup/repo/merge_request"));
    }

    @Test
    public void failsWhenRepoPathEmpty() throws Exception {
        j.createFreeStyleProject("template-mr");

        WorkflowJob job = j.createProject(WorkflowJob.class, "empty-repo");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: '', defaultTemplate: 'template-mr'",
                true));

        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        assertTrue("should mention repoPath required", run.getLog().contains("repoPath is required"));
    }

    @Test
    public void failsWhenTemplateNotFound() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "no-template");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'group/repo', defaultTemplate: 'nonexistent-template'",
                true));

        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        assertTrue("should mention template not found", run.getLog().contains("Template job not found: nonexistent-template"));
    }

    // --- US2: Skip Creation When Job Already Exists ---

    @Test
    public void skipCreationWhenJobExists() throws Exception {
        // Pre-create the folder hierarchy and target job
        Folder group = j.jenkins.createProject(Folder.class, "grp");
        Folder repo = group.createProject(Folder.class, "repo");
        repo.createProject(FreeStyleProject.class, "merge_request");

        WorkflowJob parent = j.createProject(WorkflowJob.class, "skip-parent");
        parent.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'grp/repo', defaultTemplate: 'unused-template'",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(parent.scheduleBuild2(0));
        String log = run.getLog();

        assertTrue("should log skip creation", log.contains("SKIP creation (already exists): grp/repo/merge_request"));
        assertTrue("should log trigger", log.contains("TRIGGER job: grp/repo/merge_request"));
    }

    // --- US3: Rule-Based Template Resolution ---

    @Test
    public void ruleBasedTemplateFirstMatch() throws Exception {
        j.createFreeStyleProject("templates-ip-mr");
        j.createFreeStyleProject("templates-default-mr");

        WorkflowJob job = j.createProject(WorkflowJob.class, "rule-first");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'group/abel/ip/core', defaultTemplate: 'templates-default-mr', " +
                "configuration: [templateRule(pattern: '.*abel/ip.*', finalJobNamePattern: '${REPO}/ip-mr', templateName: 'templates-ip-mr'), " +
                "templateRule(pattern: '.*', finalJobNamePattern: '${REPO}/default-mr', templateName: 'templates-default-mr')]",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        String log = run.getLog();

        assertTrue("should match first rule", log.contains("MATCH rule pattern '.*abel/ip.*'"));
        assertNotNull("target job should exist", j.jenkins.getItemByFullName("group/abel/ip/core/ip-mr"));
    }

    @Test
    public void ruleBasedTemplateFallthrough() throws Exception {
        j.createFreeStyleProject("templates-default-mr2");

        WorkflowJob job = j.createProject(WorkflowJob.class, "rule-fall");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'group/common/lib', defaultTemplate: 'templates-default-mr2', " +
                "configuration: [templateRule(pattern: '.*abel/ip.*', finalJobNamePattern: '${REPO}/ip-mr', templateName: 'nonexistent'), " +
                "templateRule(pattern: '.*', finalJobNamePattern: '${REPO}/default-mr', templateName: 'templates-default-mr2')]",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        String log = run.getLog();

        assertTrue("should match second rule", log.contains("MATCH rule pattern '.*'"));
        assertNotNull("target job should exist", j.jenkins.getItemByFullName("group/common/lib/default-mr"));
    }

    @Test
    public void defaultFallbackWhenNoRuleMatches() throws Exception {
        j.createFreeStyleProject("fallback-template");

        WorkflowJob job = j.createProject(WorkflowJob.class, "rule-default");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'group/repo', defaultTemplate: 'fallback-template', " +
                "configuration: [templateRule(pattern: '.*nomatch.*', finalJobNamePattern: '${REPO}/nomatch', templateName: 'nonexistent')]",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        String log = run.getLog();

        assertTrue("should use default", log.contains("DEFAULT template 'fallback-template'"));
        assertNotNull("target job should exist", j.jenkins.getItemByFullName("group/repo/merge_request"));
    }

    @Test
    public void repoVariableResolution() throws Exception {
        j.createFreeStyleProject("tpl-var");

        WorkflowJob job = j.createProject(WorkflowJob.class, "repo-var");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'mygroup/myrepo', defaultTemplate: 'tpl-var', " +
                "defaultTargetName: '${REPO}/custom-job'",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));

        assertNotNull("resolved target should exist", j.jenkins.getItemByFullName("mygroup/myrepo/custom-job"));
        assertTrue("should log trigger with resolved path", run.getLog().contains("TRIGGER job: mygroup/myrepo/custom-job"));
    }

    // --- US4: Race-Safe Concurrent Creation ---

    @Test
    public void concurrentCreationRaceSafe() throws Exception {
        j.createFreeStyleProject("race-template");

        // First call creates the job
        WorkflowJob job1 = j.createProject(WorkflowJob.class, "race-1");
        job1.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'race/repo', defaultTemplate: 'race-template'",
                true));
        WorkflowRun run1 = j.assertBuildStatusSuccess(job1.scheduleBuild2(0));
        assertTrue("first call should create", run1.getLog().contains("COPY template"));

        // Second call hits the already-exists path
        WorkflowJob job2 = j.createProject(WorkflowJob.class, "race-2");
        job2.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'race/repo', defaultTemplate: 'race-template'",
                true));
        WorkflowRun run2 = j.assertBuildStatusSuccess(job2.scheduleBuild2(0));
        assertTrue("second call should skip creation", run2.getLog().contains("SKIP creation (already exists)"));
        assertTrue("second call should trigger", run2.getLog().contains("TRIGGER job: race/repo/merge_request"));
    }

    // --- US5: No-Op for Skipped Repos ---

    @Test
    public void skipPatternMatchNoOp() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "skip-match");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'group/test-only/repo', defaultTemplate: 'unused', " +
                "skipPattern: '.*test-only.*'",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        String log = run.getLog();

        assertTrue("should log skip", log.contains("SKIP (skipPattern matched)"));
        assertNull("no job should be created", j.jenkins.getItemByFullName("group/test-only/repo/merge_request"));
    }

    @Test
    public void skipPatternNoMatchProceeds() throws Exception {
        j.createFreeStyleProject("skip-tpl");

        WorkflowJob job = j.createProject(WorkflowJob.class, "skip-nomatch");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'group/production/repo', defaultTemplate: 'skip-tpl', " +
                "skipPattern: '.*test-only.*'",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        assertNotNull("job should be created", j.jenkins.getItemByFullName("group/production/repo/merge_request"));
    }

    @Test
    public void noSkipPatternProceeds() throws Exception {
        j.createFreeStyleProject("noskip-tpl");

        WorkflowJob job = j.createProject(WorkflowJob.class, "noskip");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'group/noskip/repo', defaultTemplate: 'noskip-tpl'",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        assertNotNull("job should be created", j.jenkins.getItemByFullName("group/noskip/repo/merge_request"));
    }

    // --- Edge Cases ---

    @Test
    public void conflictingItemNotFolder() throws Exception {
        // Create a FreeStyleProject at a path where a folder is expected
        j.createFreeStyleProject("conflict");

        WorkflowJob job = j.createProject(WorkflowJob.class, "conflict-test");
        job.setDefinition(new CpsFlowDefinition(
                "recursiveCreateAndTrigger repoPath: 'conflict/repo', defaultTemplate: 'unused'",
                true));

        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        assertTrue("should mention not a Folder", run.getLog().contains("exists but is not a Folder"));
    }
}
