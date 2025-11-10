package com.buildstash;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import hudson.model.AbstractProject;
import java.io.IOException;

/**
 * Post-build action for uploading build artifacts to Buildstash.
 * This allows the Buildstash upload functionality to be used as a post-build action in classic Jenkins projects.
 */
public class BuildstashBuilder extends Publisher implements SimpleBuildStep {

    private Secret apiKey;
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
    public void perform(Run<?, ?> build, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        try {
            // Expand environment variables in all fields
            String apiKeyPlain = apiKey != null ? Secret.toString(apiKey) : null;
            String expandedApiKey = BuildstashUploadHelper.expand(env, apiKeyPlain);
            String expandedStructure = BuildstashUploadHelper.expand(env, structure);
            // Ensure structure defaults to "file" if not set
            if (expandedStructure == null || expandedStructure.isBlank()) {
                expandedStructure = "file";
            }
            String expandedPrimaryFilePath = BuildstashUploadHelper.expand(env, primaryFilePath);
            String expandedExpansionFilePath = BuildstashUploadHelper.expand(env, expansionFilePath);
            String expandedVersionComponent1Major = BuildstashUploadHelper.expand(env, versionComponent1Major);
            String expandedVersionComponent2Minor = BuildstashUploadHelper.expand(env, versionComponent2Minor);
            String expandedVersionComponent3Patch = BuildstashUploadHelper.expand(env, versionComponent3Patch);
            String expandedVersionComponentExtra = BuildstashUploadHelper.expand(env, versionComponentExtra);
            String expandedVersionComponentMeta = BuildstashUploadHelper.expand(env, versionComponentMeta);
            String expandedCustomBuildNumber = BuildstashUploadHelper.expand(env, customBuildNumber);
            String expandedLabels = BuildstashUploadHelper.expand(env, labels);
            String expandedArchitectures = BuildstashUploadHelper.expand(env, architectures);
            String expandedPlatform = BuildstashUploadHelper.expand(env, platform);
            String expandedStream = BuildstashUploadHelper.expand(env, stream);
            String expandedNotes = BuildstashUploadHelper.expand(env, notes);
            String expandedVcHostType = BuildstashUploadHelper.expand(env, vcHostType);
            String expandedVcHost = BuildstashUploadHelper.expand(env, vcHost);
            String expandedVcRepoName = BuildstashUploadHelper.expand(env, vcRepoName);
            String expandedVcRepoUrl = BuildstashUploadHelper.expand(env, vcRepoUrl);
            String expandedVcBranch = BuildstashUploadHelper.expand(env, vcBranch);
            String expandedVcCommitSha = BuildstashUploadHelper.expand(env, vcCommitSha);
            String expandedVcCommitUrl = BuildstashUploadHelper.expand(env, vcCommitUrl);
            
            // Validate required parameters with expanded values
            BuildstashUploadHelper.validateParameters(expandedApiKey, expandedPrimaryFilePath, expandedVersionComponent1Major,
                    expandedVersionComponent2Minor, expandedVersionComponent3Patch, expandedPlatform, expandedStream);

            // Create upload service
            BuildstashUploadService uploadService = new BuildstashUploadService(expandedApiKey, listener);

            // Prepare upload request with expanded values
            BuildstashUploadRequest request = BuildstashUploadHelper.createUploadRequest(workspace, build, expandedStructure,
                    expandedPrimaryFilePath, expandedExpansionFilePath, expandedVersionComponent1Major,
                    expandedVersionComponent2Minor, expandedVersionComponent3Patch, expandedVersionComponentExtra,
                    expandedVersionComponentMeta, expandedCustomBuildNumber, expandedLabels, expandedArchitectures,
                    expandedPlatform, expandedStream, expandedNotes, expandedVcHostType, expandedVcHost,
                    expandedVcRepoName, expandedVcRepoUrl, expandedVcBranch, expandedVcCommitSha, expandedVcCommitUrl);
            
            // Auto-detect SCM info (from project SCM config for freestyle, or BuildData for pipelines)
            VersionControlDetector.populateVersionControlInfo(build, request, listener);

            // Execute upload
            BuildstashUploadResponse response = uploadService.upload(request);

            // Log results
            BuildstashUploadHelper.logResults(listener, response);

            // Store results as build actions for later access
            build.addAction(new BuildstashBuildAction(response));

        } catch (Exception e) {
            listener.error("Buildstash upload failed: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
            throw new RuntimeException("Buildstash upload failed", e);
        }
    }


    // Getters and Setters
    public Secret getApiKey() { return apiKey; }
    
    /**
     * Setter that accepts String and converts to Secret.
     * This is the primary setter used by Jenkins pipeline scripts and forms.
     */
    @DataBoundSetter
    public void setApiKey(String apiKey) {
        this.apiKey = Secret.fromString(apiKey);
    }
    
    /**
     * Setter that accepts Secret directly (for programmatic use).
     * Note: This is NOT annotated with @DataBoundSetter to avoid DescribableModel
     * trying to coerce String to Secret. Pipeline scripts will use the String overload.
     */
    public void setApiKey(Secret apiKey) { this.apiKey = apiKey; }

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