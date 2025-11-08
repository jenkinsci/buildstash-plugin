package com.buildstash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Data model for Buildstash upload response.
 * Represents the response from the Buildstash API after a successful upload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildstashUploadResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private String message;
    
    @JsonProperty("build_id")
    private String buildId;
    
    @JsonProperty("pending_processing")
    private boolean pendingProcessing;
    
    @JsonProperty("build_info_url")
    private String buildInfoUrl;
    
    @JsonProperty("download_url")
    private String downloadUrl;

    // Default constructor for JSON deserialization
    public BuildstashUploadResponse() {}

    public BuildstashUploadResponse(String message, String buildId, boolean pendingProcessing, String buildInfoUrl, String downloadUrl) {
        this.message = message;
        this.buildId = buildId;
        this.pendingProcessing = pendingProcessing;
        this.buildInfoUrl = buildInfoUrl;
        this.downloadUrl = downloadUrl;
    }

    // Getters and Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }

    public boolean isPendingProcessing() { return pendingProcessing; }
    public void setPendingProcessing(boolean pendingProcessing) { this.pendingProcessing = pendingProcessing; }

    public String getBuildInfoUrl() { return buildInfoUrl; }
    public void setBuildInfoUrl(String buildInfoUrl) { this.buildInfoUrl = buildInfoUrl; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
} 