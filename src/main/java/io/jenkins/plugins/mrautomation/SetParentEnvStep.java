package io.jenkins.plugins.mrautomation;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class SetParentEnvStep extends Step {

    @DataBoundConstructor
    public SetParentEnvStep() {}

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

            Cause.UpstreamCause upstreamCause = null;
            for (Cause cause : run.getCauses()) {
                if (cause instanceof Cause.UpstreamCause) {
                    upstreamCause = (Cause.UpstreamCause) cause;
                }
            }

            if (upstreamCause == null) {
                listener.getLogger().println("[setParentEnv] No upstream parent found. No-op.");
                return null;
            }

            Run<?, ?> parentRun = upstreamCause.getUpstreamRun();
            if (parentRun == null) {
                listener.getLogger().println("[setParentEnv] Parent build no longer exists. No-op.");
                return null;
            }

            listener.getLogger().println("[setParentEnv] Parent build: " + parentRun.getFullDisplayName());

            // Collect parent env from all sources:
            // 1. Run.getEnvironment() — system env + ParametersAction + EnvironmentContributingAction
            EnvVars parentEnv = parentRun.getEnvironment(listener);
            // 2. EnvironmentContributingActions (includes InjectEnvVarsAction from previous setParentEnv calls)
            for (EnvironmentContributingAction action : parentRun.getActions(EnvironmentContributingAction.class)) {
                action.buildEnvironment(parentRun, parentEnv);
            }
            // 3. Pipeline CPS env overrides (env.KEY = value)
            EnvActionImpl parentEnvAction = parentRun.getAction(EnvActionImpl.class);
            if (parentEnvAction != null) {
                parentEnv.putAll(parentEnvAction.getOverriddenEnvironment());
            }

            Map<String, String> toInject = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : parentEnv.entrySet()) {
                if (!currentEnv.containsKey(entry.getKey())) {
                    toInject.put(entry.getKey(), entry.getValue());
                }
            }

            if (!toInject.isEmpty()) {
                // Pipeline CPS env — visible to env.KEY in current build's pipeline
                EnvActionImpl envAction = EnvActionImpl.forRun(run);
                for (Map.Entry<String, String> entry : toInject.entrySet()) {
                    envAction.setProperty(entry.getKey(), entry.getValue());
                }
                // Run-level action — visible to child builds via Run.getEnvironment()
                run.addAction(new InjectEnvVarsAction(toInject));
                listener.getLogger().println("[setParentEnv] Injected " + toInject.size() + " variables from parent build.");
            } else {
                listener.getLogger().println("[setParentEnv] No new variables to inject from parent build.");
            }

            return null;
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "setParentEnv";
        }

        @Override
        public String getDisplayName() {
            return "Inject environment variables from parent (upstream) build";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class, EnvVars.class);
        }
    }
}
