package io.jenkins.plugins.mrautomation;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import hudson.model.AbstractDescribableImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class RecursiveCreateAndTriggerStep extends Step {

    private final String repoPath;
    private final String defaultTemplate;
    private List<TemplateRule> configuration;
    private String defaultTargetName = "${REPO}/merge_request";
    private String skipPattern;

    @DataBoundConstructor
    public RecursiveCreateAndTriggerStep(String repoPath, String defaultTemplate) {
        this.repoPath = repoPath;
        this.defaultTemplate = defaultTemplate;
    }

    public String getRepoPath() { return repoPath; }
    public String getDefaultTemplate() { return defaultTemplate; }
    public List<TemplateRule> getConfiguration() { return configuration; }
    public String getDefaultTargetName() { return defaultTargetName; }
    public String getSkipPattern() { return skipPattern; }

    @DataBoundSetter
    public void setConfiguration(List<TemplateRule> configuration) { this.configuration = configuration; }

    @DataBoundSetter
    public void setDefaultTargetName(String defaultTargetName) { this.defaultTargetName = defaultTargetName; }

    @DataBoundSetter
    public void setSkipPattern(String skipPattern) { this.skipPattern = skipPattern; }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    public static class TemplateRule extends AbstractDescribableImpl<TemplateRule> {

        private final String pattern;
        private final String finalJobNamePattern;
        private final String templateName;

        @DataBoundConstructor
        public TemplateRule(String pattern, String finalJobNamePattern, String templateName) {
            this.pattern = pattern;
            this.finalJobNamePattern = finalJobNamePattern;
            this.templateName = templateName;
        }

        public String getPattern() { return pattern; }
        public String getFinalJobNamePattern() { return finalJobNamePattern; }
        public String getTemplateName() { return templateName; }

        @Extension @Symbol("templateRule")
        public static class DescriptorImpl extends Descriptor<TemplateRule> {}
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private final transient RecursiveCreateAndTriggerStep step;

        Execution(StepContext context, RecursiveCreateAndTriggerStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            Run<?, ?> currentRun = getContext().get(Run.class);
            String repoPath = step.getRepoPath();

            // FR-013: validate repoPath
            if (repoPath == null || repoPath.isBlank()) {
                throw new AbortException("[recursiveCreateAndTrigger] repoPath is required but was null or empty");
            }

            // FR-005: check skipPattern first
            String skipPattern = step.getSkipPattern();
            if (skipPattern != null && !skipPattern.isEmpty() && repoPath.matches(skipPattern)) {
                listener.getLogger().println("[recursiveCreateAndTrigger] SKIP (skipPattern matched): " + repoPath);
                return null;
            }

            // FR-005: resolve template and target via rules or defaults
            String targetName = null;
            String templatePath = null;
            List<TemplateRule> config = step.getConfiguration();
            if (config != null) {
                for (TemplateRule rule : config) {
                    if (repoPath.matches(rule.getPattern())) {
                        targetName = resolveRepoVariable(rule.getFinalJobNamePattern(), repoPath);
                        templatePath = rule.getTemplateName();
                        listener.getLogger().println("[recursiveCreateAndTrigger] MATCH rule pattern '" + rule.getPattern() + "' -> template '" + templatePath + "'");
                        break;
                    }
                }
            }
            // FR-014: fall back to defaults
            if (targetName == null) {
                targetName = resolveRepoVariable(step.getDefaultTargetName(), repoPath);
                templatePath = step.getDefaultTemplate();
                listener.getLogger().println("[recursiveCreateAndTrigger] DEFAULT template '" + templatePath + "' -> target '" + targetName + "'");
            }

            // FR-009: skip creation if target already exists
            if (Jenkins.get().getItemByFullName(targetName) != null) {
                listener.getLogger().println("[recursiveCreateAndTrigger] SKIP creation (already exists): " + targetName);
                triggerJob(targetName, currentRun, listener);
                return null;
            }

            // FR-006 + FR-007: create folder hierarchy and copy template
            String jobLeafName = targetName.substring(targetName.lastIndexOf('/') + 1);
            ItemGroup<?> parent = createFolderHierarchy(targetName, listener);
            copyTemplateJob(parent, jobLeafName, templatePath, listener);

            // FR-008: trigger
            triggerJob(targetName, currentRun, listener);
            return null;
        }

        private String resolveRepoVariable(String pattern, String repoPath) {
            return pattern.replace("${REPO}", repoPath);
        }

        private Folder getOrCreateFolder(ItemGroup<?> parent, String name, TaskListener listener) throws IOException {
            Item existing = parent.getItem(name);
            if (existing instanceof Folder) {
                return (Folder) existing;
            }
            if (existing != null) {
                throw new AbortException(parent.getFullName() + "/" + name + " exists but is not a Folder (type: " + existing.getClass().getSimpleName() + ")");
            }
            try {
                Folder created = ((Folder) parent).createProject(Folder.class, name);
                listener.getLogger().println("[recursiveCreateAndTrigger] CREATE folder: " + created.getFullName());
                return created;
            } catch (IllegalArgumentException e) {
                existing = parent.getItem(name);
                if (existing instanceof Folder) {
                    return (Folder) existing;
                }
                throw new IOException("Failed to create folder: " + name, e);
            }
        }

        private ItemGroup<?> createFolderHierarchy(String targetJobPath, TaskListener listener) throws IOException {
            String[] segments = targetJobPath.split("/");
            ItemGroup<?> current = Jenkins.get();
            for (int i = 0; i < segments.length - 1; i++) {
                if (current instanceof Jenkins) {
                    Item item = ((Jenkins) current).getItem(segments[i]);
                    if (item instanceof Folder) {
                        current = (Folder) item;
                    } else if (item == null) {
                        Folder created = Jenkins.get().createProject(Folder.class, segments[i]);
                        listener.getLogger().println("[recursiveCreateAndTrigger] CREATE folder: " + created.getFullName());
                        current = created;
                    } else {
                        throw new AbortException(segments[i] + " exists but is not a Folder (type: " + item.getClass().getSimpleName() + ")");
                    }
                } else {
                    current = getOrCreateFolder(current, segments[i], listener);
                }
            }
            return current;
        }

        private void copyTemplateJob(ItemGroup<?> parent, String jobName, String templatePath, TaskListener listener) throws IOException {
            TopLevelItem template = Jenkins.get().getItemByFullName(templatePath, TopLevelItem.class);
            if (template == null) {
                throw new AbortException("Template job not found: " + templatePath);
            }
            try {
                if (parent instanceof Folder) {
                    ((Folder) parent).copy(template, jobName);
                } else {
                    Jenkins.get().copy(template, jobName);
                }
                listener.getLogger().println("[recursiveCreateAndTrigger] COPY template '" + templatePath + "' -> '" + parent.getFullName() + "/" + jobName + "'");
            } catch (IllegalArgumentException e) {
                String fullPath = parent.getFullName().isEmpty() ? jobName : parent.getFullName() + "/" + jobName;
                if (Jenkins.get().getItemByFullName(fullPath) != null) {
                    listener.getLogger().println("[recursiveCreateAndTrigger] SKIP copy (already exists): " + fullPath);
                } else {
                    throw new IOException("Failed to copy template to: " + fullPath, e);
                }
            }
        }

        private void triggerJob(String targetJobPath, Run<?, ?> currentRun, TaskListener listener) throws AbortException {
            Item item = Jenkins.get().getItemByFullName(targetJobPath);
            if (item == null) {
                throw new AbortException("Target job not found after creation: " + targetJobPath);
            }
            if (!(item instanceof Job)) {
                throw new AbortException("Target is not a Job: " + targetJobPath);
            }
            Jenkins.get().getQueue().schedule2((hudson.model.Queue.Task) item, 0,
                    new CauseAction(new Cause.UpstreamCause(currentRun)));
            listener.getLogger().println("[recursiveCreateAndTrigger] TRIGGER job: " + targetJobPath);
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "recursiveCreateAndTrigger";
        }

        @Override
        public String getDisplayName() {
            return "Recursively create folder hierarchy, copy template job, and trigger it";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class);
        }
    }
}
