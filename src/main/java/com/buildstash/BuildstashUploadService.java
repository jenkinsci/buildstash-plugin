package com.buildstash;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.FilePath;
import hudson.model.TaskListener;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Service class for uploading files to Buildstash.
 * Handles HTTP communication with the Buildstash API.
 */
public class BuildstashUploadService {

    private static final String API_BASE_URL = "http://app.buildstash.com/api/v1";
    private static final String UPLOAD_REQUEST_ENDPOINT = API_BASE_URL + "/upload/request";
    private static final String UPLOAD_VERIFY_ENDPOINT = API_BASE_URL + "/upload/verify";
    private static final String MULTIPART_REQUEST_ENDPOINT = API_BASE_URL + "/upload/request/multipart";
    private static final String MULTIPART_EXPANSION_ENDPOINT = API_BASE_URL + "/upload/request/multipart/expansion";

    private final String apiKey;
    private final TaskListener listener;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BuildstashUploadService(String apiKey, TaskListener listener) {
        // Enable manual Content-Length header
        // Note: Host header is automatically set by HTTP client from URI and cannot be set manually
        // The system property must be set before HttpClient is created
        String existingHeaders = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        if (existingHeaders == null || !existingHeaders.contains("Content-Length")) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", 
                existingHeaders == null ? "Content-Length" : existingHeaders + ",Content-Length");
        }

        this.apiKey = apiKey;
        this.listener = listener;
        this.objectMapper = new ObjectMapper();
        // Configure HttpClient to follow redirects automatically
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public BuildstashUploadResponse upload(BuildstashUploadRequest request) throws Exception {
        // Step 1: Request upload URLs
        listener.getLogger().println("Requesting upload URLs from Buildstash...");
        UploadRequestResponse uploadRequestResponse = requestUploadUrls(request);

        // Step 2: Upload files
        listener.getLogger().println("Uploading files to Buildstash...");
        List<MultipartChunk> primaryFileParts = null;
        List<MultipartChunk> expansionFileParts = null;
        
        // Upload primary file
        if (uploadRequestResponse.getPrimaryFile().isChunkedUpload()) {
            listener.getLogger().println("Uploading primary file using chunked upload...");
            primaryFileParts = uploadChunkedFile(
                request.getWorkspace().child(request.getPrimaryFilePath()),
                uploadRequestResponse.getPendingUploadId(),
                uploadRequestResponse.getPrimaryFile(),
                false
            );
        } else {
            listener.getLogger().println("Uploading primary file using direct upload...");
            uploadDirectFile(
                request.getWorkspace().child(request.getPrimaryFilePath()),
                uploadRequestResponse.getPrimaryFile().getPresignedData()
            );
        }

        // Upload expansion file if present
        if (request.getExpansionFilePath() != null && uploadRequestResponse.getExpansionFiles() != null && !uploadRequestResponse.getExpansionFiles().isEmpty()) {
            FileUploadInfo expansionFile = uploadRequestResponse.getExpansionFiles().get(0);
            if (expansionFile.isChunkedUpload()) {
                listener.getLogger().println("Uploading expansion file using chunked upload...");
                expansionFileParts = uploadChunkedFile(
                    request.getWorkspace().child(request.getExpansionFilePath()),
                    uploadRequestResponse.getPendingUploadId(),
                    expansionFile,
                    true
                );
            } else {
                listener.getLogger().println("Uploading expansion file using direct upload...");
                uploadDirectFile(
                    request.getWorkspace().child(request.getExpansionFilePath()),
                    expansionFile.getPresignedData()
                );
            }
        }

        // Step 3: Verify upload
        listener.getLogger().println("Verifying upload...");
        return verifyUpload(uploadRequestResponse.getPendingUploadId(), primaryFileParts, expansionFileParts);
    }

    private UploadRequestResponse requestUploadUrls(BuildstashUploadRequest request) throws Exception {
        // Build request payload
        Map<String, Object> payload = request.toMap();
        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(UPLOAD_REQUEST_ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            String responseBody = response.body();
            
            // Log error response for user visibility
            listener.error("Server returned error: " + response.statusCode());
            listener.error("Error response: " + responseBody);
            
            throw new RuntimeException("Failed to request upload URLs: " + response.statusCode() + " - " + responseBody);
        }

        String contentType = response.headers().firstValue("content-type").orElse("unknown");
        String responseBody = response.body();
        
        // Check if response is actually JSON
        if (!contentType.contains("application/json") && !contentType.contains("json")) {
            throw new RuntimeException("Server returned HTML instead of JSON. This usually indicates an authentication error or the API endpoint is incorrect. Response content-type: " + contentType);
        }

        try {
            return objectMapper.readValue(responseBody, UploadRequestResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response: " + e.getMessage(), e);
        }
    }

    private List<MultipartChunk> uploadChunkedFile(FilePath filePath, String pendingUploadId, FileUploadInfo fileInfo, boolean isExpansion) throws Exception {
        String endpoint = isExpansion ? MULTIPART_EXPANSION_ENDPOINT : MULTIPART_REQUEST_ENDPOINT;
        long fileSize = filePath.length();
        int chunkSize = fileInfo.getChunkedPartSizeMb() * 1024 * 1024;
        int numberOfParts = fileInfo.getChunkedNumberParts();

        for (int i = 0; i < numberOfParts; i++) {
            int partNumber = i + 1;
            long chunkStart = i * chunkSize;
            long chunkEnd = Math.min((i + 1) * chunkSize - 1, fileSize - 1);
            long contentLength = chunkEnd - chunkStart + 1;

            listener.getLogger().println("Uploading chunked upload, part: " + partNumber + " of " + numberOfParts);

            // Request presigned URL for this part
            PresignedUrlResponse presignedResponse = requestPresignedUrl(endpoint, pendingUploadId, partNumber, contentLength);

            // Upload chunk via presigned URL
            uploadChunk(filePath, presignedResponse.getPartPresignedUrl(), chunkStart, chunkEnd, contentLength);
        }

        // For simplicity, we'll return null for parts array in this implementation
        // In a full implementation, you'd collect ETags from each upload
        return null;
    }

    private PresignedUrlResponse requestPresignedUrl(String endpoint, String pendingUploadId, int partNumber, long contentLength) throws Exception {
        Map<String, Object> payload = Map.of(
            "pending_upload_id", pendingUploadId,
            "part_number", partNumber,
            "content_length", contentLength
        );

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get presigned URL: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), PresignedUrlResponse.class);
    }

    private void uploadChunk(FilePath filePath, String presignedUrl, long start, long end, long contentLength) throws Exception {
        // Create input stream for the chunk
        try (InputStream inputStream = filePath.read()) {
            // Skip to start position
            long skipped = inputStream.skip(start);
            if (skipped != start) {
                throw new IOException("Failed to skip to position " + start + ", only skipped " + skipped + " bytes");
            }
            
            // Create a limited input stream for the chunk
            InputStream limitedInputStream = new java.io.FilterInputStream(inputStream) {
                private long remaining = contentLength;
                
                @Override
                public int read() throws IOException {
                    if (remaining <= 0) return -1;
                    int result = super.read();
                    if (result != -1) remaining--;
                    return result;
                }
                
                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (remaining <= 0) return -1;
                    int toRead = (int) Math.min(len, remaining);
                    int result = super.read(b, off, toRead);
                    if (result > 0) remaining -= result;
                    return result;
                }
            };

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(presignedUrl))
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Length", String.valueOf(contentLength))
                    .PUT(BodyPublishers.ofInputStream(() -> limitedInputStream))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to upload chunk: " + response.statusCode() + " - " + response.body());
            }
        }
    }

    private void uploadDirectFile(FilePath filePath, PresignedData presignedData) throws Exception {
        String url = presignedData.getUrl();
        
        if (url == null || url.trim().isEmpty()) {
            throw new RuntimeException("Presigned URL is null or empty");
        }
        
        // Get headers from presigned data
        // The signature includes: host, content-disposition, x-amz-acl
        // Note: Host header is automatically set by HTTP client from URI (cannot be set manually)
        // This matches the working GitHub Actions implementation which doesn't set Host
        String contentType = presignedData.getHeaderAsString("Content-Type");
        String contentDisposition = presignedData.getHeaderAsString("Content-Disposition");
        String xAmzAcl = presignedData.getHeaderAsString("x-amz-acl");
        String contentLengthStr = presignedData.getHeaderAsString("Content-Length");

        // Verify file size matches Content-Length from presigned data
        long fileSize = filePath.length();
        if (contentLengthStr != null) {
            long expectedContentLength = Long.parseLong(contentLengthStr);
            if (fileSize != expectedContentLength) {
                throw new RuntimeException(
                    String.format("File size mismatch: expected %d bytes, but file is %d bytes", 
                        expectedContentLength, fileSize)
                );
            }
        }

        // Read file into byte array to ensure exact Content-Length matching
        // This is critical for AWS signature validation - the body must match exactly
        byte[] fileBytes;
        try (InputStream inputStream = filePath.read()) {
            fileBytes = inputStream.readAllBytes();
            if (fileBytes.length != fileSize) {
                throw new RuntimeException(
                    String.format("File read mismatch: expected %d bytes, but read %d bytes", 
                        fileSize, fileBytes.length)
                );
            }
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        // Set headers exactly as in the working GitHub Actions implementation
        // Order matches: Content-Type, Content-Length, Content-Disposition, x-amz-acl
        // Note: Host header is automatically set by HTTP client from URI (cannot be set manually)
        if (contentType != null) {
            requestBuilder.header("Content-Type", contentType);
        }
        if (contentLengthStr != null) {
            requestBuilder.header("Content-Length", contentLengthStr);
        }
        if (contentDisposition != null) {
            requestBuilder.header("Content-Disposition", contentDisposition);
        }
        if (xAmzAcl != null) {
            requestBuilder.header("x-amz-acl", xAmzAcl);
        }

        requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes));

        HttpRequest httpRequest = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to upload file: " + response.statusCode() + " - " + response.body());
        }
    }

    private BuildstashUploadResponse verifyUpload(String pendingUploadId, List<MultipartChunk> primaryFileParts, List<MultipartChunk> expansionFileParts) throws Exception {
        // Build verify payload
        Map<String, Object> payload = Map.of("pending_upload_id", pendingUploadId);
        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(UPLOAD_VERIFY_ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to verify upload: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), BuildstashUploadResponse.class);
    }

    public void close() throws IOException {
        // HttpClient doesn't need to be closed as it's managed by the JVM
        // This method is kept for compatibility but does nothing
    }
} 