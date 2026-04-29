package io.jenkins.plugins.mrautomation;

import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Map;

import static org.junit.Assert.*;

public class SetParentEnvStepTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void parentEnvInherited() throws Exception {
        // Parent freestyle job — its env vars come from InjectEnvVarsAction
        FreeStyleProject parent = j.createFreeStyleProject("parent");
        FreeStyleBuild parentBuild = j.buildAndAssertSuccess(parent);
        parentBuild.addAction(new InjectEnvVarsAction(Map.of(
                "gitlabSourceBranch", "feature/test",
                "gitlabTargetBranch", "main",
                "CUSTOM_VAR", "from-parent"
        )));
        parentBuild.save();

        // Child pipeline calls setParentEnv
        WorkflowJob child = j.createProject(WorkflowJob.class, "child");
        child.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  echo \"SOURCE=${env.gitlabSourceBranch}\"\n" +
                "  echo \"TARGET=${env.gitlabTargetBranch}\"\n" +
                "  echo \"CUSTOM=${env.CUSTOM_VAR}\"\n" +
                "}", true));

        WorkflowRun childRun = j.assertBuildStatusSuccess(
                child.scheduleBuild2(0, new CauseAction(new Cause.UpstreamCause(parentBuild))));

        String log = childRun.getLog();
        assertLog(log, "SOURCE=feature/test");
        assertLog(log, "TARGET=main");
        assertLog(log, "CUSTOM=from-parent");
        assertLog(log, "[setParentEnv] Injected");
    }

    @Test
    public void noOpWithoutParent() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "orphan");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  setParentEnv()\n" +
                "  echo 'done'\n" +
                "}", true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        assertLog(run.getLog(), "[setParentEnv] No upstream parent found. No-op.");
    }

    @Test
    public void childVarsNotOverwritten() throws Exception {
        FreeStyleProject parent = j.createFreeStyleProject("parent2");
        FreeStyleBuild parentBuild = j.buildAndAssertSuccess(parent);
        parentBuild.addAction(new InjectEnvVarsAction(Map.of(
                "MY_VAR", "parent-value"
        )));
        parentBuild.save();

        WorkflowJob child = j.createProject(WorkflowJob.class, "child2");
        child.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  withEnv(['MY_VAR=child-value']) {\n" +
                "    setParentEnv()\n" +
                "    echo \"MY_VAR=${env.MY_VAR}\"\n" +
                "  }\n" +
                "}", true));

        WorkflowRun childRun = j.assertBuildStatusSuccess(
                child.scheduleBuild2(0, new CauseAction(new Cause.UpstreamCause(parentBuild))));

        assertLog(childRun.getLog(), "MY_VAR=child-value");
    }

    private void assertLog(String log, String expected) {
        assertTrue("Expected log to contain: " + expected + "\nActual log:\n" + log,
                log.contains(expected));
    }
}
