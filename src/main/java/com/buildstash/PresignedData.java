package com.buildstash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Data model for presigned URL data from Buildstash.
 * Contains the URL and headers needed for direct file uploads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PresignedData {

    @JsonProperty("url")
    private String url;
    
    @JsonProperty("headers")
    private Map<String, Object> headers;

    // Default constructor for JSON deserialization
    public PresignedData() {}

    public PresignedData(String url, Map<String, Object> headers) {
        this.url = url;
        this.headers = headers;
    }

    // Getters and Setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Map<String, Object> getHeaders() { return headers; }
    public void setHeaders(Map<String, Object> headers) { this.headers = headers; }
    
    /**
     * Helper method to get a header value as a string, handling arrays by taking the first element.
     */
    public String getHeaderAsString(String key) {
        Object value = headers.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            if (!list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof String) {
                    return (String) first;
                }
                return first.toString();
            }
        }
        return value.toString();
    }
} 