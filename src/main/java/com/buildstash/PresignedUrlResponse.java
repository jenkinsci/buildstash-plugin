package com.buildstash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model for presigned URL response from Buildstash.
 * Contains the presigned URL for uploading a specific chunk.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PresignedUrlResponse {

    private String message;
    
    @JsonProperty("part_presigned_url")
    private String partPresignedUrl;
    
    @JsonProperty("part_number")
    private int partNumber;

    // Default constructor for JSON deserialization
    public PresignedUrlResponse() {}

    public PresignedUrlResponse(String message, String partPresignedUrl, int partNumber) {
        this.message = message;
        this.partPresignedUrl = partPresignedUrl;
        this.partNumber = partNumber;
    }

    // Getters and Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPartPresignedUrl() { return partPresignedUrl; }
    public void setPartPresignedUrl(String partPresignedUrl) { this.partPresignedUrl = partPresignedUrl; }

    public int getPartNumber() { return partNumber; }
    public void setPartNumber(int partNumber) { this.partNumber = partNumber; }
} 