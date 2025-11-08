package com.buildstash;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * Execution class for the Buildstash step.
 * Handles the actual upload process to the Buildstash service.
 */
public class BuildstashStepExecution extends SynchronousNonBlockingStepExecution<Void> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final BuildstashStep step;

    public BuildstashStepExecution(BuildstashStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    protected Void run() throws Exception {
        TaskListener listener = getContext().get(TaskListener.class);
        FilePath workspace = getContext().get(FilePath.class);
        Run<?, ?> run = getContext().get(Run.class);

        if (listener == null) {
            throw new IllegalStateException("TaskListener not available");
        }

        if (workspace == null) {
            throw new IllegalStateException("Workspace not available");
        }

        // Validate required parameters
        validateParameters();

        // Create upload service
        BuildstashUploadService uploadService = new BuildstashUploadService(step.getApiKey(), listener);

        // Prepare upload request
        BuildstashUploadRequest request = createUploadRequest(workspace, run);

        // Execute upload
        BuildstashUploadResponse response = uploadService.upload(request);

        // Log results
        listener.getLogger().println("Buildstash upload completed successfully!");
        listener.getLogger().println("Build ID: " + response.getBuildId());
        listener.getLogger().println("Build Info URL: " + response.getBuildInfoUrl());
        listener.getLogger().println("Download URL: " + response.getDownloadUrl());
        listener.getLogger().println("Pending Processing: " + response.isPendingProcessing());

        return null;
    }

    private void validateParameters() throws Exception {
        if (step.getApiKey() == null || step.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("API key is required");
        }

        if (step.getPrimaryFilePath() == null || step.getPrimaryFilePath().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary file path is required");
        }

        if (step.getVersionComponent1Major() == null || step.getVersionComponent1Major().trim().isEmpty()) {
            throw new IllegalArgumentException("Major version component is required");
        }

        if (step.getVersionComponent2Minor() == null || step.getVersionComponent2Minor().trim().isEmpty()) {
            throw new IllegalArgumentException("Minor version component is required");
        }

        if (step.getVersionComponent3Patch() == null || step.getVersionComponent3Patch().trim().isEmpty()) {
            throw new IllegalArgumentException("Patch version component is required");
        }

        if (step.getPlatform() == null || step.getPlatform().trim().isEmpty()) {
            throw new IllegalArgumentException("Platform is required");
        }

        if (step.getStream() == null || step.getStream().trim().isEmpty()) {
            throw new IllegalArgumentException("Stream is required");
        }
    }

    private BuildstashUploadRequest createUploadRequest(FilePath workspace, Run<?, ?> run) throws IOException, InterruptedException {
        BuildstashUploadRequest request = new BuildstashUploadRequest();

        // Set basic properties
        request.setStructure(step.getStructure());
        request.setPrimaryFilePath(step.getPrimaryFilePath());
        request.setExpansionFilePath(step.getExpansionFilePath());
        request.setVersionComponent1Major(step.getVersionComponent1Major());
        request.setVersionComponent2Minor(step.getVersionComponent2Minor());
        request.setVersionComponent3Patch(step.getVersionComponent3Patch());
        request.setVersionComponentExtra(step.getVersionComponentExtra());
        request.setVersionComponentMeta(step.getVersionComponentMeta());
        request.setCustomBuildNumber(step.getCustomBuildNumber());
        request.setPlatform(step.getPlatform());
        request.setStream(step.getStream());
        request.setNotes(step.getNotes());

        // Parse labels and architectures
        if (step.getLabels() != null && !step.getLabels().trim().isEmpty()) {
            List<String> labels = Arrays.asList(step.getLabels().split("\\r?\\n"));
            request.setLabels(labels);
        }

        if (step.getArchitectures() != null && !step.getArchitectures().trim().isEmpty()) {
            List<String> architectures = Arrays.asList(step.getArchitectures().split("\\r?\\n"));
            request.setArchitectures(architectures);
        }

        // Set CI information automatically from Jenkins context
        request.setCiPipeline(run.getParent().getDisplayName());
        request.setCiRunId(String.valueOf(run.getNumber()));
        request.setCiRunUrl(getBuildUrl(run));
        request.setCiBuildDuration(formatBuildDuration(run.getDuration()));
        request.setSource("jenkins");

        // Set version control information
        request.setVcHostType(step.getVcHostType());
        request.setVcHost(step.getVcHost());
        request.setVcRepoName(step.getVcRepoName());
        request.setVcRepoUrl(step.getVcRepoUrl());
        request.setVcBranch(step.getVcBranch());
        request.setVcCommitSha(step.getVcCommitSha());
        request.setVcCommitUrl(step.getVcCommitUrl());

        // Set workspace for file operations
        request.setWorkspace(workspace);

        return request;
    }

    /**
     * Gets the full URL to the build run status summary.
     */
    private String getBuildUrl(Run<?, ?> build) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            String rootUrl = jenkins.getRootUrl();
            if (rootUrl != null && !rootUrl.isEmpty()) {
                // Remove trailing slash from root URL if present
                String baseUrl = rootUrl.endsWith("/") ? rootUrl.substring(0, rootUrl.length() - 1) : rootUrl;
                String buildPath = build.getUrl();
                // Ensure build path starts with / if it doesn't already
                if (!buildPath.startsWith("/")) {
                    buildPath = "/" + buildPath;
                }
                return baseUrl + buildPath;
            }
        }
        // Fallback to relative URL if root URL is not available
        return build.getUrl();
    }

    /**
     * Formats build duration in milliseconds to HH:mm:ss format.
     */
    private String formatBuildDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
} 