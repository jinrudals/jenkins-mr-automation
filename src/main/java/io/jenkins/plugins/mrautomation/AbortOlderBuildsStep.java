package io.jenkins.plugins.mrautomation;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class AbortOlderBuildsStep extends Step {

    @DataBoundConstructor
    public AbortOlderBuildsStep() {}

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context);
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        Execution(StepContext context) {
            super(context);
        }

        @Override
        protected Void run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);
            EnvVars currentEnv = getContext().get(EnvVars.class);

            String myMrIid = currentEnv.get("gitlabMergeRequestIid", "");
            String mySourceBranch = currentEnv.get("gitlabSourceBranch", "");
            String myTargetBranch = currentEnv.get("gitlabTargetBranch", "");
            int myBuildNumber = run.getNumber();

            listener.getLogger().println("[abortOlderBuilds] Current build #" + myBuildNumber
                    + ": mrIid=" + myMrIid + ", source=" + mySourceBranch + ", target=" + myTargetBranch);

            if (myMrIid.isEmpty() && mySourceBranch.isEmpty()) {
                listener.getLogger().println("[abortOlderBuilds] No MR IID or branch info available. No-op.");
                return null;
            }

            Job<?, ?> job = run.getParent();
            int abortedCount = 0;

            for (Run<?, ?> b : job.getBuilds()) {
                if (!b.isBuilding() || b.getNumber() >= myBuildNumber) {
                    continue;
                }

                String bMrIid = null;
                String bSourceBranch = null;
                String bTargetBranch = null;

                Cause.UpstreamCause upstreamCause = null;
                for (Cause cause : b.getCauses()) {
                    if (cause instanceof Cause.UpstreamCause) {
                        upstreamCause = (Cause.UpstreamCause) cause;
                    }
                }

                // Try upstream parent env first, fall back to candidate's own env
                if (upstreamCause != null) {
                    Run<?, ?> parentRun = upstreamCause.getUpstreamRun();
                    if (parentRun != null) {
                        EnvVars parentEnv = parentRun.getEnvironment(TaskListener.NULL);
                        bMrIid = parentEnv.get("gitlabMergeRequestIid", "");
                        bSourceBranch = parentEnv.get("gitlabSourceBranch", "");
                        bTargetBranch = parentEnv.get("gitlabTargetBranch", "");
                    } else {
                        listener.getLogger().println("[abortOlderBuilds] Build #" + b.getNumber()
                                + ": upstream parent deleted. Skipping.");
                        continue;
                    }
                }

                // Fallback: read candidate build's own env (covers parameter-based jobs)
                if ((bMrIid == null || bMrIid.isEmpty()) && (bSourceBranch == null || bSourceBranch.isEmpty())) {
                    EnvVars bEnv = b.getEnvironment(TaskListener.NULL);
                    bMrIid = bEnv.get("gitlabMergeRequestIid", "");
                    bSourceBranch = bEnv.get("gitlabSourceBranch", "");
                    bTargetBranch = bEnv.get("gitlabTargetBranch", "");
                }

                if ((bMrIid == null || bMrIid.isEmpty()) && (bSourceBranch == null || bSourceBranch.isEmpty())) {
                    continue;
                }

                boolean match = false;
                String reason = "";

                if (!myMrIid.isEmpty() && myMrIid.equals(bMrIid)) {
                    match = true;
                    reason = "same MR IID: " + myMrIid;
                } else if (myMrIid.isEmpty() && !mySourceBranch.isEmpty()
                        && mySourceBranch.equals(bSourceBranch) && myTargetBranch.equals(bTargetBranch)) {
                    match = true;
                    reason = "same branch: " + mySourceBranch + " → " + myTargetBranch;
                }

                if (match) {
                    listener.getLogger().println("[abortOlderBuilds] Aborting build #" + b.getNumber()
                            + " (" + reason + ")");
                    Executor executor = b.getExecutor();
                    if (executor != null) {
                        executor.interrupt(Result.ABORTED);
                    }
                    abortedCount++;
                }
            }

            if (abortedCount > 0) {
                listener.getLogger().println("[abortOlderBuilds] Aborted " + abortedCount + " older build(s).");
            } else {
                listener.getLogger().println("[abortOlderBuilds] No older builds to abort.");
            }

            return null;
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "abortOlderBuilds";
        }

        @Override
        public String getDisplayName() {
            return "Abort older running builds of the same Job for the same MR or branch";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class, EnvVars.class);
        }
    }
}
