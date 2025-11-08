package com.buildstash;

import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import hudson.util.ListBoxModel;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * Pipeline step for uploading build artifacts to Buildstash.
 * This step can be used in Jenkins pipelines to upload files to the Buildstash service.
 */
public class BuildstashStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

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
    public BuildstashStep() {
        // Default constructor required for Jenkins
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new BuildstashStepExecution(this, context);
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
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "buildstash";
        }

        @Override
        public String getDisplayName() {
            return "Upload to Buildstash";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

        @Override
        public boolean isAdvanced() {
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