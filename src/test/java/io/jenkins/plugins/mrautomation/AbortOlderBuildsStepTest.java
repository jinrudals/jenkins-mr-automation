package io.jenkins.plugins.mrautomation;

import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Map;

import static org.junit.Assert.*;

public class AbortOlderBuildsStepTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    // --- US1: MR IID matching ---

    @Test
    public void sameMrIidAbortsOlderBuild() throws Exception {
        FreeStyleProject parent = j.createFreeStyleProject("parent-mr");
        FreeStyleBuild parentBuild = j.buildAndAssertSuccess(parent);
        parentBuild.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabMergeRequestIid", "42",
                "gitlabSourceBranch", "feature/x",
                "gitlabTargetBranch", "main"
        )));
        parentBuild.save();

        WorkflowJob child = j.createProject(WorkflowJob.class, "child-mr");
        child.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  semaphore 'wait'\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        // Build #1 — blocks on semaphore
        WorkflowRun run1 = child.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(parentBuild))).waitForStart();
        SemaphoreStep.waitForStart("wait/1", run1);

        // Build #2 — release it immediately, it will abort build #1
        WorkflowRun run2 = child.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(parentBuild))).waitForStart();
        SemaphoreStep.waitForStart("wait/2", run2);
        SemaphoreStep.success("wait/2", null);

        j.waitForCompletion(run2);
        assertEquals(Result.SUCCESS, run2.getResult());

        j.waitForCompletion(run1);
        assertEquals(Result.ABORTED, run1.getResult());
        assertLog(run2.getLog(), "Aborting build #1");
        assertLog(run2.getLog(), "same MR IID: 42");
    }

    @Test
    public void differentMrIidsNoAbort() throws Exception {
        FreeStyleProject parent1 = j.createFreeStyleProject("parent-mr1");
        FreeStyleBuild parentBuild1 = j.buildAndAssertSuccess(parent1);
        parentBuild1.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabMergeRequestIid", "42"
        )));
        parentBuild1.save();

        FreeStyleProject parent2 = j.createFreeStyleProject("parent-mr2");
        FreeStyleBuild parentBuild2 = j.buildAndAssertSuccess(parent2);
        parentBuild2.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabMergeRequestIid", "99"
        )));
        parentBuild2.save();

        WorkflowJob child = j.createProject(WorkflowJob.class, "child-diff-mr");
        child.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  semaphore 'wait'\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        WorkflowRun run1 = child.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(parentBuild1))).waitForStart();
        SemaphoreStep.waitForStart("wait/1", run1);

        WorkflowRun run2 = child.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(parentBuild2))).waitForStart();
        SemaphoreStep.waitForStart("wait/2", run2);
        SemaphoreStep.success("wait/2", null);

        j.waitForCompletion(run2);
        assertEquals(Result.SUCCESS, run2.getResult());
        assertLog(run2.getLog(), "No older builds to abort.");

        // Release run1 so it can finish
        SemaphoreStep.success("wait/1", null);
        j.waitForCompletion(run1);
        assertEquals(Result.SUCCESS, run1.getResult());
    }

    // --- US2: Branch combination fallback ---

    @Test
    public void sameBranchNoMrIidAbortsOlderBuild() throws Exception {
        FreeStyleProject parent = j.createFreeStyleProject("parent-branch");
        FreeStyleBuild parentBuild = j.buildAndAssertSuccess(parent);
        parentBuild.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabSourceBranch", "feature/y",
                "gitlabTargetBranch", "main"
        )));
        parentBuild.save();

        WorkflowJob child = j.createProject(WorkflowJob.class, "child-branch");
        child.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  semaphore 'wait'\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        WorkflowRun run1 = child.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(parentBuild))).waitForStart();
        SemaphoreStep.waitForStart("wait/1", run1);

        WorkflowRun run2 = child.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(parentBuild))).waitForStart();
        SemaphoreStep.waitForStart("wait/2", run2);
        SemaphoreStep.success("wait/2", null);

        j.waitForCompletion(run2);
        assertEquals(Result.SUCCESS, run2.getResult());

        j.waitForCompletion(run1);
        assertEquals(Result.ABORTED, run1.getResult());
        assertLog(run2.getLog(), "same branch: feature/y");
    }

    @Test
    public void sameSourceDifferentTargetNoAbort() throws Exception {
        FreeStyleProject parent1 = j.createFreeStyleProject("parent-b1");
        FreeStyleBuild parentBuild1 = j.buildAndAssertSuccess(parent1);
        parentBuild1.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabSourceBranch", "feature/z",
                "gitlabTargetBranch", "main"
        )));
        parentBuild1.save();

        FreeStyleProject parent2 = j.createFreeStyleProject("parent-b2");
        FreeStyleBuild parentBuild2 = j.buildAndAssertSuccess(parent2);
        parentBuild2.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabSourceBranch", "feature/z",
                "gitlabTargetBranch", "develop"
        )));
        parentBuild2.save();

        WorkflowJob child = j.createProject(WorkflowJob.class, "child-diff-target");
        child.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  semaphore 'wait'\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        WorkflowRun run1 = child.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(parentBuild1))).waitForStart();
        SemaphoreStep.waitForStart("wait/1", run1);

        WorkflowRun run2 = child.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(parentBuild2))).waitForStart();
        SemaphoreStep.waitForStart("wait/2", run2);
        SemaphoreStep.success("wait/2", null);

        j.waitForCompletion(run2);
        assertEquals(Result.SUCCESS, run2.getResult());

        SemaphoreStep.success("wait/1", null);
        j.waitForCompletion(run1);
        assertEquals(Result.SUCCESS, run1.getResult());
    }

    // --- US3: No-op / edge cases ---

    @Test
    public void noOpSingleBuild() throws Exception {
        FreeStyleProject parent = j.createFreeStyleProject("parent-single");
        FreeStyleBuild parentBuild = j.buildAndAssertSuccess(parent);
        parentBuild.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabMergeRequestIid", "10"
        )));
        parentBuild.save();

        WorkflowJob child = j.createProject(WorkflowJob.class, "child-single");
        child.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        WorkflowRun run = j.assertBuildStatusSuccess(
                child.scheduleBuild2(0, new CauseAction(new Cause.UpstreamCause(parentBuild))));
        assertLog(run.getLog(), "No older builds to abort.");
    }

    @Test
    public void noOpWithoutUpstreamParent() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "orphan-abort");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        assertLog(run.getLog(), "No MR IID or branch info available. No-op.");
    }

    // --- E2E: Multi-job topology ---

    @Test
    public void e2eEnvironmentInheritanceAbortsOlderChild() throws Exception {
        // Job1: parent with parameters (simulated via InjectEnvVarsAction on FreeStyle)
        FreeStyleProject job1 = j.createFreeStyleProject("e2e-parent-env");
        FreeStyleBuild job1Build = j.buildAndAssertSuccess(job1);
        job1Build.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabMergeRequestIid", "42",
                "gitlabSourceBranch", "feature/e2e",
                "gitlabTargetBranch", "main"
        )));
        job1Build.save();

        // Job2: child with setParentEnv + abortOlderBuilds
        WorkflowJob job2 = j.createProject(WorkflowJob.class, "e2e-child-env");
        job2.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  semaphore 'e2e-env'\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        // Trigger job2 build #1 (blocks on semaphore)
        WorkflowRun child1 = job2.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(job1Build))).waitForStart();
        SemaphoreStep.waitForStart("e2e-env/1", child1);

        // Trigger job2 build #2 (same parent = same MR IID)
        WorkflowRun child2 = job2.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(job1Build))).waitForStart();
        SemaphoreStep.waitForStart("e2e-env/2", child2);
        SemaphoreStep.success("e2e-env/2", null);

        j.waitForCompletion(child2);
        assertEquals(Result.SUCCESS, child2.getResult());

        j.waitForCompletion(child1);
        assertEquals(Result.ABORTED, child1.getResult());
        assertLog(child2.getLog(), "Aborting build #1");
    }

    @Test
    public void e2eParameterForwardingAbortsOlderChild() throws Exception {
        // Job1: parent with parameters
        FreeStyleProject job1 = j.createFreeStyleProject("e2e-parent-param");
        FreeStyleBuild job1Build = j.buildAndAssertSuccess(job1);
        job1Build.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabMergeRequestIid", "99",
                "gitlabSourceBranch", "feature/param",
                "gitlabTargetBranch", "main"
        )));
        job1Build.save();

        // Job3: child with own parameters + setParentEnv + abortOlderBuilds
        WorkflowJob job3 = j.createProject(WorkflowJob.class, "e2e-child-param");
        job3.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  semaphore 'e2e-param'\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        // Trigger job3 build #1 with UpstreamCause + ParametersAction
        WorkflowRun child1 = job3.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(job1Build)),
                new hudson.model.ParametersAction(
                        new hudson.model.StringParameterValue("gitlabMergeRequestIid", "99"),
                        new hudson.model.StringParameterValue("gitlabSourceBranch", "feature/param"),
                        new hudson.model.StringParameterValue("gitlabTargetBranch", "main")
                )).waitForStart();
        SemaphoreStep.waitForStart("e2e-param/1", child1);

        // Trigger job3 build #2 with same upstream cause and parameters
        WorkflowRun child2 = job3.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(job1Build)),
                new hudson.model.ParametersAction(
                        new hudson.model.StringParameterValue("gitlabMergeRequestIid", "99"),
                        new hudson.model.StringParameterValue("gitlabSourceBranch", "feature/param"),
                        new hudson.model.StringParameterValue("gitlabTargetBranch", "main")
                )).waitForStart();
        SemaphoreStep.waitForStart("e2e-param/2", child2);
        SemaphoreStep.success("e2e-param/2", null);

        j.waitForCompletion(child2);
        assertEquals(Result.SUCCESS, child2.getResult());

        j.waitForCompletion(child1);
        assertEquals(Result.ABORTED, child1.getResult());
        assertLog(child2.getLog(), "Aborting build #1");
        assertLog(child2.getLog(), "same MR IID: 99");
    }

    @Test
    public void e2eDifferentMrIidsNoAbort() throws Exception {
        // Two different parent builds with different MR IIDs
        FreeStyleProject job1 = j.createFreeStyleProject("e2e-parent-diff");
        FreeStyleBuild job1Build1 = j.buildAndAssertSuccess(job1);
        job1Build1.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabMergeRequestIid", "50"
        )));
        job1Build1.save();

        FreeStyleBuild job1Build2 = j.buildAndAssertSuccess(job1);
        job1Build2.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabMergeRequestIid", "51"
        )));
        job1Build2.save();

        WorkflowJob job2 = j.createProject(WorkflowJob.class, "e2e-child-diff");
        job2.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  semaphore 'e2e-diff'\n" +
                "  abortOlderBuilds()\n" +
                "  echo 'done'\n" +
                "}", true));

        WorkflowRun child1 = job2.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(job1Build1))).waitForStart();
        SemaphoreStep.waitForStart("e2e-diff/1", child1);

        WorkflowRun child2 = job2.scheduleBuild2(0,
                new CauseAction(new Cause.UpstreamCause(job1Build2))).waitForStart();
        SemaphoreStep.waitForStart("e2e-diff/2", child2);
        SemaphoreStep.success("e2e-diff/2", null);

        j.waitForCompletion(child2);
        assertEquals(Result.SUCCESS, child2.getResult());
        assertLog(child2.getLog(), "No older builds to abort.");

        SemaphoreStep.success("e2e-diff/1", null);
        j.waitForCompletion(child1);
        assertEquals(Result.SUCCESS, child1.getResult());
    }

    private void assertLog(String log, String expected) {
        assertTrue("Expected log to contain: " + expected + "\nActual log:\n" + log,
                log.contains(expected));
    }
}
