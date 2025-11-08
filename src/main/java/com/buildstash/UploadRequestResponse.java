package com.buildstash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Data model for the initial upload request response from Buildstash.
 * Contains upload URLs and metadata for file uploads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadRequestResponse {

    private String message;
    
    @JsonProperty("pending_upload_id")
    private String pendingUploadId;
    
    @JsonProperty("primary_file")
    private FileUploadInfo primaryFile;
    
    @JsonProperty("expansion_files")
    private List<FileUploadInfo> expansionFiles;

    // Default constructor for JSON deserialization
    public UploadRequestResponse() {}

    public UploadRequestResponse(String message, String pendingUploadId, FileUploadInfo primaryFile, List<FileUploadInfo> expansionFiles) {
        this.message = message;
        this.pendingUploadId = pendingUploadId;
        this.primaryFile = primaryFile;
        this.expansionFiles = expansionFiles;
    }

    // Getters and Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPendingUploadId() { return pendingUploadId; }
    public void setPendingUploadId(String pendingUploadId) { this.pendingUploadId = pendingUploadId; }

    public FileUploadInfo getPrimaryFile() { return primaryFile; }
    public void setPrimaryFile(FileUploadInfo primaryFile) { this.primaryFile = primaryFile; }

    public List<FileUploadInfo> getExpansionFiles() { return expansionFiles; }
    public void setExpansionFiles(List<FileUploadInfo> expansionFiles) { this.expansionFiles = expansionFiles; }
} 