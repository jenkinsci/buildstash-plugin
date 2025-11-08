package com.buildstash;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Post-build action for uploading build artifacts to Buildstash.
 * This allows the Buildstash upload functionality to be used as a post-build action in classic Jenkins projects.
 */
public class BuildstashBuilder extends Publisher implements SimpleBuildStep {

    private String apiKey;
    private String structure = "file";
    private String primaryFilePath;
    private String expansionFilePath;
    private String versionComponent1Major;
    private String versionComponent2Minor;
    private String versionComponent3Patch;
    private String versionComponentExtra;
    private String versionComponentMeta;
    private String customBuildNumber;
    private String labels;
    private String architectures;
    private String vcHostType = "git";
    private String vcHost = "github";
    private String vcRepoName;
    private String vcRepoUrl;
    private String vcBranch;
    private String vcCommitSha;
    private String vcCommitUrl;
    private String platform;
    private String stream;
    private String notes;

    @DataBoundConstructor
    public BuildstashBuilder() {
        // Default constructor required for Jenkins
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        try {
            // Validate required parameters
            validateParameters();

            // Create upload service
            BuildstashUploadService uploadService = new BuildstashUploadService(apiKey, listener);

            // Prepare upload request
            BuildstashUploadRequest request = createUploadRequest(workspace, build);

            // Execute upload
            BuildstashUploadResponse response = uploadService.upload(request);

            // Log results
            listener.getLogger().println("Buildstash upload completed successfully!");
            listener.getLogger().println("Build ID: " + response.getBuildId());
            listener.getLogger().println("Build Info URL: " + response.getBuildInfoUrl());
            listener.getLogger().println("Download URL: " + response.getDownloadUrl());
            listener.getLogger().println("Pending Processing: " + response.isPendingProcessing());

            // Store results as build actions for later access
            build.addAction(new BuildstashBuildAction(response));

        } catch (Exception e) {
            listener.error("Buildstash upload failed: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
            throw new RuntimeException("Buildstash upload failed", e);
        }
    }

    private void validateParameters() throws Exception {
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

    private BuildstashUploadRequest createUploadRequest(FilePath workspace, Run<?, ?> build) throws IOException, InterruptedException {
        BuildstashUploadRequest request = new BuildstashUploadRequest();

        // Set basic properties
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

        // Parse labels and architectures
        if (labels != null && !labels.trim().isEmpty()) {
            List<String> labelsList = Arrays.asList(labels.split("\\r?\\n"));
            request.setLabels(labelsList);
        }

        if (architectures != null && !architectures.trim().isEmpty()) {
            List<String> architecturesList = Arrays.asList(architectures.split("\\r?\\n"));
            request.setArchitectures(architecturesList);
        }

        // Set CI information automatically from Jenkins context
        request.setCiPipeline(build.getParent().getDisplayName());
        request.setCiRunId(String.valueOf(build.getNumber()));
        request.setCiRunUrl(getBuildUrl(build));
        request.setCiPipelineUrl(getProjectUrl(build));
        request.setCiBuildDuration(formatBuildDuration(getBuildDuration(build)));
        request.setSource("jenkins");

        // Set version control information (manual values first)
        request.setVcHostType(vcHostType);
        request.setVcHost(vcHost);
        request.setVcRepoName(vcRepoName);
        request.setVcRepoUrl(vcRepoUrl);
        request.setVcBranch(vcBranch);
        request.setVcCommitSha(vcCommitSha);
        request.setVcCommitUrl(vcCommitUrl);

        // Auto-detect and populate any missing VC fields from Jenkins
        VersionControlDetector.populateVersionControlInfo(build, request);

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

    // Getters and Setters
    public String getApiKey() { return apiKey; }
    
    @DataBoundSetter
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getStructure() { return structure; }
    
    @DataBoundSetter
    public void setStructure(String structure) { this.structure = structure; }

    public String getPrimaryFilePath() { return primaryFilePath; }
    
    @DataBoundSetter
    public void setPrimaryFilePath(String primaryFilePath) { this.primaryFilePath = primaryFilePath; }

    public String getExpansionFilePath() { return expansionFilePath; }
    
    @DataBoundSetter
    public void setExpansionFilePath(String expansionFilePath) { this.expansionFilePath = expansionFilePath; }

    public String getVersionComponent1Major() { return versionComponent1Major; }
    
    @DataBoundSetter
    public void setVersionComponent1Major(String versionComponent1Major) { this.versionComponent1Major = versionComponent1Major; }

    public String getVersionComponent2Minor() { return versionComponent2Minor; }
    
    @DataBoundSetter
    public void setVersionComponent2Minor(String versionComponent2Minor) { this.versionComponent2Minor = versionComponent2Minor; }

    public String getVersionComponent3Patch() { return versionComponent3Patch; }
    
    @DataBoundSetter
    public void setVersionComponent3Patch(String versionComponent3Patch) { this.versionComponent3Patch = versionComponent3Patch; }

    public String getVersionComponentExtra() { return versionComponentExtra; }
    
    @DataBoundSetter
    public void setVersionComponentExtra(String versionComponentExtra) { this.versionComponentExtra = versionComponentExtra; }

    public String getVersionComponentMeta() { return versionComponentMeta; }
    
    @DataBoundSetter
    public void setVersionComponentMeta(String versionComponentMeta) { this.versionComponentMeta = versionComponentMeta; }

    public String getCustomBuildNumber() { return customBuildNumber; }
    
    @DataBoundSetter
    public void setCustomBuildNumber(String customBuildNumber) { this.customBuildNumber = customBuildNumber; }

    public String getLabels() { return labels; }
    
    @DataBoundSetter
    public void setLabels(String labels) { this.labels = labels; }

    public String getArchitectures() { return architectures; }
    
    @DataBoundSetter
    public void setArchitectures(String architectures) { this.architectures = architectures; }

    public String getVcHostType() { return vcHostType; }
    
    @DataBoundSetter
    public void setVcHostType(String vcHostType) { this.vcHostType = vcHostType; }

    public String getVcHost() { return vcHost; }
    
    @DataBoundSetter
    public void setVcHost(String vcHost) { this.vcHost = vcHost; }

    public String getVcRepoName() { return vcRepoName; }
    
    @DataBoundSetter
    public void setVcRepoName(String vcRepoName) { this.vcRepoName = vcRepoName; }

    public String getVcRepoUrl() { return vcRepoUrl; }
    
    @DataBoundSetter
    public void setVcRepoUrl(String vcRepoUrl) { this.vcRepoUrl = vcRepoUrl; }

    public String getVcBranch() { return vcBranch; }
    
    @DataBoundSetter
    public void setVcBranch(String vcBranch) { this.vcBranch = vcBranch; }

    public String getVcCommitSha() { return vcCommitSha; }
    
    @DataBoundSetter
    public void setVcCommitSha(String vcCommitSha) { this.vcCommitSha = vcCommitSha; }

    public String getVcCommitUrl() { return vcCommitUrl; }
    
    @DataBoundSetter
    public void setVcCommitUrl(String vcCommitUrl) { this.vcCommitUrl = vcCommitUrl; }

    public String getPlatform() { return platform; }
    
    @DataBoundSetter
    public void setPlatform(String platform) { this.platform = platform; }

    public String getStream() { return stream; }
    
    @DataBoundSetter
    public void setStream(String stream) { this.stream = stream; }

    public String getNotes() { return notes; }
    
    @DataBoundSetter
    public void setNotes(String notes) { this.notes = notes; }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return "Upload to Buildstash";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public ListBoxModel doFillStructureItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("File", "file");
            items.add("File + Expansion", "file+expansion");
            return items;
        }
    }
} 