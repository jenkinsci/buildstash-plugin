package com.buildstash;

import hudson.EnvVars;
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
import java.util.stream.Collectors;

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
        EnvVars env = getContext().get(EnvVars.class);

        if (listener == null) {
            throw new IllegalStateException("TaskListener not available");
        }

        if (workspace == null) {
            throw new IllegalStateException("Workspace not available");
        }

        // Get environment variables for expansion (may be null in some contexts)
        if (env == null) {
            try {
                env = run.getEnvironment(listener);
            } catch (Exception e) {
                // If we can't get environment, create empty one
                env = new EnvVars();
            }
        }
        
        // Expand environment variables in all fields
        String expandedApiKey = expand(env, step.getApiKey());
        String expandedStructure = expand(env, step.getStructure());
        String expandedPrimaryFilePath = expand(env, step.getPrimaryFilePath());
        String expandedExpansionFilePath = expand(env, step.getExpansionFilePath());
        String expandedVersionComponent1Major = expand(env, step.getVersionComponent1Major());
        String expandedVersionComponent2Minor = expand(env, step.getVersionComponent2Minor());
        String expandedVersionComponent3Patch = expand(env, step.getVersionComponent3Patch());
        String expandedVersionComponentExtra = expand(env, step.getVersionComponentExtra());
        String expandedVersionComponentMeta = expand(env, step.getVersionComponentMeta());
        String expandedCustomBuildNumber = expand(env, step.getCustomBuildNumber());
        String expandedLabels = expand(env, step.getLabels());
        String expandedArchitectures = expand(env, step.getArchitectures());
        String expandedPlatform = expand(env, step.getPlatform());
        String expandedStream = expand(env, step.getStream());
        String expandedNotes = expand(env, step.getNotes());
        String expandedVcHostType = expand(env, step.getVcHostType());
        String expandedVcHost = expand(env, step.getVcHost());
        String expandedVcRepoName = expand(env, step.getVcRepoName());
        String expandedVcRepoUrl = expand(env, step.getVcRepoUrl());
        String expandedVcBranch = expand(env, step.getVcBranch());
        String expandedVcCommitSha = expand(env, step.getVcCommitSha());
        String expandedVcCommitUrl = expand(env, step.getVcCommitUrl());

        // Validate required parameters with expanded values
        validateParameters(expandedApiKey, expandedPrimaryFilePath, expandedVersionComponent1Major,
                expandedVersionComponent2Minor, expandedVersionComponent3Patch, expandedPlatform, expandedStream);

        // Create upload service
        BuildstashUploadService uploadService = new BuildstashUploadService(expandedApiKey, listener);

        // Prepare upload request with expanded values
        BuildstashUploadRequest request = createUploadRequest(workspace, run, expandedStructure,
                expandedPrimaryFilePath, expandedExpansionFilePath, expandedVersionComponent1Major,
                expandedVersionComponent2Minor, expandedVersionComponent3Patch, expandedVersionComponentExtra,
                expandedVersionComponentMeta, expandedCustomBuildNumber, expandedLabels, expandedArchitectures,
                expandedPlatform, expandedStream, expandedNotes, expandedVcHostType, expandedVcHost,
                expandedVcRepoName, expandedVcRepoUrl, expandedVcBranch, expandedVcCommitSha, expandedVcCommitUrl);

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

    private void validateParameters(String apiKey, String primaryFilePath, String versionComponent1Major,
                                    String versionComponent2Minor, String versionComponent3Patch,
                                    String platform, String stream) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key is required");
        }

        if (primaryFilePath == null || primaryFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary file path is required");
        }

        if (versionComponent1Major == null || versionComponent1Major.trim().isEmpty()) {
            throw new IllegalArgumentException("Major version component is required");
        }

        if (versionComponent2Minor == null || versionComponent2Minor.trim().isEmpty()) {
            throw new IllegalArgumentException("Minor version component is required");
        }

        if (versionComponent3Patch == null || versionComponent3Patch.trim().isEmpty()) {
            throw new IllegalArgumentException("Patch version component is required");
        }

        if (platform == null || platform.trim().isEmpty()) {
            throw new IllegalArgumentException("Platform is required");
        }

        if (stream == null || stream.trim().isEmpty()) {
            throw new IllegalArgumentException("Stream is required");
        }
    }
    
    /**
     * Expands environment variables in a string value.
     * Returns null if input is null, otherwise expands variables like ${VAR_NAME}.
     */
    private String expand(EnvVars env, String value) {
        if (value == null || env == null) {
            return value;
        }
        return env.expand(value);
    }

    private BuildstashUploadRequest createUploadRequest(FilePath workspace, Run<?, ?> run,
                                                         String structure, String primaryFilePath, String expansionFilePath,
                                                         String versionComponent1Major, String versionComponent2Minor,
                                                         String versionComponent3Patch, String versionComponentExtra,
                                                         String versionComponentMeta, String customBuildNumber,
                                                         String labels, String architectures, String platform,
                                                         String stream, String notes, String vcHostType, String vcHost,
                                                         String vcRepoName, String vcRepoUrl, String vcBranch,
                                                         String vcCommitSha, String vcCommitUrl) throws IOException, InterruptedException {
        BuildstashUploadRequest request = new BuildstashUploadRequest();

        // Set basic properties (already expanded)
        request.setStructure(structure);
        request.setPrimaryFilePath(primaryFilePath);
        request.setExpansionFilePath(expansionFilePath);
        request.setVersionComponent1Major(versionComponent1Major);
        request.setVersionComponent2Minor(versionComponent2Minor);
        request.setVersionComponent3Patch(versionComponent3Patch);
        request.setVersionComponentExtra(versionComponentExtra);
        request.setVersionComponentMeta(versionComponentMeta);
        request.setCustomBuildNumber(customBuildNumber);
        request.setPlatform(platform);
        request.setStream(stream);
        request.setNotes(notes);

        // Parse labels and architectures (comma-separated or newline-separated)
        if (labels != null && !labels.trim().isEmpty()) {
            List<String> labelsList = Arrays.stream(labels.split("[,\\r\\n]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            request.setLabels(labelsList);
        }

        if (architectures != null && !architectures.trim().isEmpty()) {
            List<String> architecturesList = Arrays.stream(architectures.split("[,\\r\\n]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            request.setArchitectures(architecturesList);
        }

        // Set CI information automatically from Jenkins context
        request.setCiPipeline(run.getParent().getDisplayName());
        request.setCiRunId(String.valueOf(run.getNumber()));
        request.setCiRunUrl(getBuildUrl(run));
        request.setCiPipelineUrl(getProjectUrl(run));
        request.setCiBuildDuration(formatBuildDuration(getBuildDuration(run)));
        request.setSource("jenkins");

        // Set version control information (manual values first, already expanded)
        request.setVcHostType(vcHostType);
        request.setVcHost(vcHost);
        request.setVcRepoName(vcRepoName);
        request.setVcRepoUrl(vcRepoUrl);
        request.setVcBranch(vcBranch);
        request.setVcCommitSha(vcCommitSha);
        request.setVcCommitUrl(vcCommitUrl);

        // Auto-detect and populate any missing VC fields from Jenkins
        VersionControlDetector.populateVersionControlInfo(run, request);

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
     * Gets the full URL to the Jenkins project/job root.
     */
    private String getProjectUrl(Run<?, ?> build) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            String rootUrl = jenkins.getRootUrl();
            if (rootUrl != null && !rootUrl.isEmpty()) {
                // Remove trailing slash from root URL if present
                String baseUrl = rootUrl.endsWith("/") ? rootUrl.substring(0, rootUrl.length() - 1) : rootUrl;
                String projectPath = build.getParent().getUrl();
                // Ensure project path starts with / if it doesn't already
                if (!projectPath.startsWith("/")) {
                    projectPath = "/" + projectPath;
                }
                return baseUrl + projectPath;
            }
        }
        // Fallback to relative URL if root URL is not available
        return build.getParent().getUrl();
    }

    /**
     * Gets the build duration in milliseconds.
     * If the build is still running or duration is 0, calculates duration from start time to now.
     * If the build is completed with a valid duration, returns that duration.
     */
    private long getBuildDuration(Run<?, ?> build) {
        long duration = build.getDuration();
        // If duration is 0 (build still running or not set), calculate from start time
        if (duration == 0) {
            long startTime = build.getStartTimeInMillis();
            long currentTime = System.currentTimeMillis();
            duration = currentTime - startTime;
        }
        return duration;
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