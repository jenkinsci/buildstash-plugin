package com.buildstash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model for file upload information from Buildstash.
 * Contains metadata about how to upload a file (direct or chunked).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileUploadInfo {

    private String filename;
    
    @JsonProperty("chunked_upload")
    private boolean chunkedUpload;
    
    @JsonProperty("chunked_number_parts")
    private int chunkedNumberParts;
    
    @JsonProperty("chunked_part_size_mb")
    private int chunkedPartSizeMb;
    
    @JsonProperty("presigned_data")
    private PresignedData presignedData;

    // Default constructor for JSON deserialization
    public FileUploadInfo() {}

    public FileUploadInfo(String filename, boolean chunkedUpload, int chunkedNumberParts, int chunkedPartSizeMb, PresignedData presignedData) {
        this.filename = filename;
        this.chunkedUpload = chunkedUpload;
        this.chunkedNumberParts = chunkedNumberParts;
        this.chunkedPartSizeMb = chunkedPartSizeMb;
        this.presignedData = presignedData;
    }

    // Getters and Setters
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public boolean isChunkedUpload() { return chunkedUpload; }
    public void setChunkedUpload(boolean chunkedUpload) { this.chunkedUpload = chunkedUpload; }

    public int getChunkedNumberParts() { return chunkedNumberParts; }
    public void setChunkedNumberParts(int chunkedNumberParts) { this.chunkedNumberParts = chunkedNumberParts; }

    public int getChunkedPartSizeMb() { return chunkedPartSizeMb; }
    public void setChunkedPartSizeMb(int chunkedPartSizeMb) { this.chunkedPartSizeMb = chunkedPartSizeMb; }

    public PresignedData getPresignedData() { return presignedData; }
    public void setPresignedData(PresignedData presignedData) { this.presignedData = presignedData; }
} 