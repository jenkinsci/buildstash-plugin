package com.buildstash;

import hudson.FilePath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model for Buildstash upload request.
 * Represents the payload sent to the Buildstash API.
 */
public class BuildstashUploadRequest {

    private String structure;
    private String primaryFilePath;
    private String expansionFilePath;
    private String versionComponent1Major;
    private String versionComponent2Minor;
    private String versionComponent3Patch;
    private String versionComponentExtra;
    private String versionComponentMeta;
    private String customBuildNumber;
    private List<String> labels;
    private List<String> architectures;
    private String source;
    private String ciPipeline;
    private String ciRunId;
    private String ciRunUrl;
    private String ciPipelineUrl;
    private String ciBuildDuration;
    private String vcHostType;
    private String vcHost;
    private String vcRepoName;
    private String vcRepoUrl;
    private String vcBranch;
    private String vcCommitSha;
    private String vcCommitUrl;
    private String platform;
    private String stream;
    private String notes;
    private FilePath workspace;

    // Getters and Setters
    public String getStructure() { return structure; }
    public void setStructure(String structure) { this.structure = structure; }

    public String getPrimaryFilePath() { return primaryFilePath; }
    public void setPrimaryFilePath(String primaryFilePath) { this.primaryFilePath = primaryFilePath; }

    public String getExpansionFilePath() { return expansionFilePath; }
    public void setExpansionFilePath(String expansionFilePath) { this.expansionFilePath = expansionFilePath; }

    public String getVersionComponent1Major() { return versionComponent1Major; }
    public void setVersionComponent1Major(String versionComponent1Major) { this.versionComponent1Major = versionComponent1Major; }

    public String getVersionComponent2Minor() { return versionComponent2Minor; }
    public void setVersionComponent2Minor(String versionComponent2Minor) { this.versionComponent2Minor = versionComponent2Minor; }

    public String getVersionComponent3Patch() { return versionComponent3Patch; }
    public void setVersionComponent3Patch(String versionComponent3Patch) { this.versionComponent3Patch = versionComponent3Patch; }

    public String getVersionComponentExtra() { return versionComponentExtra; }
    public void setVersionComponentExtra(String versionComponentExtra) { this.versionComponentExtra = versionComponentExtra; }

    public String getVersionComponentMeta() { return versionComponentMeta; }
    public void setVersionComponentMeta(String versionComponentMeta) { this.versionComponentMeta = versionComponentMeta; }

    public String getCustomBuildNumber() { return customBuildNumber; }
    public void setCustomBuildNumber(String customBuildNumber) { this.customBuildNumber = customBuildNumber; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public List<String> getArchitectures() { return architectures; }
    public void setArchitectures(List<String> architectures) { this.architectures = architectures; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCiPipeline() { return ciPipeline; }
    public void setCiPipeline(String ciPipeline) { this.ciPipeline = ciPipeline; }

    public String getCiRunId() { return ciRunId; }
    public void setCiRunId(String ciRunId) { this.ciRunId = ciRunId; }

    public String getCiRunUrl() { return ciRunUrl; }
    public void setCiRunUrl(String ciRunUrl) { this.ciRunUrl = ciRunUrl; }

    public String getCiPipelineUrl() { return ciPipelineUrl; }
    public void setCiPipelineUrl(String ciPipelineUrl) { this.ciPipelineUrl = ciPipelineUrl; }

    public String getCiBuildDuration() { return ciBuildDuration; }
    public void setCiBuildDuration(String ciBuildDuration) { this.ciBuildDuration = ciBuildDuration; }

    public String getVcHostType() { return vcHostType; }
    public void setVcHostType(String vcHostType) { this.vcHostType = vcHostType; }

    public String getVcHost() { return vcHost; }
    public void setVcHost(String vcHost) { this.vcHost = vcHost; }

    public String getVcRepoName() { return vcRepoName; }
    public void setVcRepoName(String vcRepoName) { this.vcRepoName = vcRepoName; }

    public String getVcRepoUrl() { return vcRepoUrl; }
    public void setVcRepoUrl(String vcRepoUrl) { this.vcRepoUrl = vcRepoUrl; }

    public String getVcBranch() { return vcBranch; }
    public void setVcBranch(String vcBranch) { this.vcBranch = vcBranch; }

    public String getVcCommitSha() { return vcCommitSha; }
    public void setVcCommitSha(String vcCommitSha) { this.vcCommitSha = vcCommitSha; }

    public String getVcCommitUrl() { return vcCommitUrl; }
    public void setVcCommitUrl(String vcCommitUrl) { this.vcCommitUrl = vcCommitUrl; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getStream() { return stream; }
    public void setStream(String stream) { this.stream = stream; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public FilePath getWorkspace() { return workspace; }
    public void setWorkspace(FilePath workspace) { this.workspace = workspace; }

    /**
     * Converts this request to a Map for JSON serialization.
     * This method builds the payload that matches the GitHub Actions implementation.
     */
    public Map<String, Object> toMap() throws Exception {
        Map<String, Object> payload = new HashMap<>();

        // Basic structure and file info
        payload.put("structure", structure);
        
        // Primary file info
        if (primaryFilePath != null && workspace != null) {
            FilePath primaryFile = workspace.child(primaryFilePath);
            if (primaryFile.exists()) {
                Map<String, Object> primaryFileInfo = new HashMap<>();
                primaryFileInfo.put("filename", primaryFile.getName());
                primaryFileInfo.put("size_bytes", primaryFile.length());
                payload.put("primary_file", primaryFileInfo);
            }
        }

        // Expansion file info
        if (structure != null && structure.equals("file+expansion") && expansionFilePath != null && workspace != null) {
            FilePath expansionFile = workspace.child(expansionFilePath);
            if (expansionFile.exists()) {
                Map<String, Object> expansionFileInfo = new HashMap<>();
                expansionFileInfo.put("filename", expansionFile.getName());
                expansionFileInfo.put("size_bytes", expansionFile.length());
                payload.put("expansion_files", List.of(expansionFileInfo));
            }
        }

        // Version components
        payload.put("version_component_1_major", versionComponent1Major);
        payload.put("version_component_2_minor", versionComponent2Minor);
        payload.put("version_component_3_patch", versionComponent3Patch);
        if (versionComponentExtra != null) {
            payload.put("version_component_extra", versionComponentExtra);
        }
        if (versionComponentMeta != null) {
            payload.put("version_component_meta", versionComponentMeta);
        }
        if (customBuildNumber != null) {
            payload.put("custom_build_number", customBuildNumber);
        }

        // Labels and architectures
        if (labels != null && !labels.isEmpty()) {
            payload.put("labels", labels);
        }
        if (architectures != null && !architectures.isEmpty()) {
            payload.put("architectures", architectures);
        }

        // Source
        payload.put("source", source);

        // CI information
        if (ciPipeline != null) {
            payload.put("ci_pipeline", ciPipeline);
        }
        if (ciRunId != null) {
            payload.put("ci_run_id", ciRunId);
        }
        if (ciRunUrl != null) {
            payload.put("ci_run_url", ciRunUrl);
        }
        if (ciPipelineUrl != null) {
            payload.put("ci_pipeline_url", ciPipelineUrl);
        }
        if (ciBuildDuration != null) {
            payload.put("ci_build_duration", ciBuildDuration);
        }

        // Version control information
        if (vcHostType != null) {
            payload.put("vc_host_type", vcHostType);
        }
        if (vcHost != null) {
            payload.put("vc_host", vcHost);
        }
        if (vcRepoName != null) {
            payload.put("vc_repo_name", vcRepoName);
        }
        if (vcRepoUrl != null) {
            payload.put("vc_repo_url", vcRepoUrl);
        }
        if (vcBranch != null) {
            payload.put("vc_branch", vcBranch);
        }
        if (vcCommitSha != null) {
            payload.put("vc_commit_sha", vcCommitSha);
        }
        if (vcCommitUrl != null) {
            payload.put("vc_commit_url", vcCommitUrl);
        }

        // Platform and stream
        payload.put("platform", platform);
        payload.put("stream", stream);

        // Notes
        if (notes != null) {
            payload.put("notes", notes);
        }

        return payload;
    }
} 