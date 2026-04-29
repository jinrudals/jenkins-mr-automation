package io.jenkins.plugins.mrautomation;

import hudson.EnvVars;
import hudson.model.InvisibleAction;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class InjectEnvVarsAction extends InvisibleAction implements EnvironmentContributingAction, Serializable {

    private static final long serialVersionUID = 1L;

    private final HashMap<String, String> envVars;

    public InjectEnvVarsAction(Map<String, String> envVars) {
        this.envVars = new HashMap<>(envVars);
    }

    @Override
    public void buildEnvironment(Run<?, ?> run, EnvVars env) {
        env.putAll(envVars);
    }
}
