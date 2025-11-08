package com.buildstash;

/**
 * Data model for multipart upload chunk information.
 * Represents a part of a multipart upload with its part number and ETag.
 */
public class MultipartChunk {

    private int partNumber;
    private String eTag;

    // Default constructor for JSON deserialization
    public MultipartChunk() {}

    public MultipartChunk(int partNumber, String eTag) {
        this.partNumber = partNumber;
        this.eTag = eTag;
    }

    // Getters and Setters
    public int getPartNumber() { return partNumber; }
    public void setPartNumber(int partNumber) { this.partNumber = partNumber; }

    public String getETag() { return eTag; }
    public void setETag(String eTag) { this.eTag = eTag; }
} 