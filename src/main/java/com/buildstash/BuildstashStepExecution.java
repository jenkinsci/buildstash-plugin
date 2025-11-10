package com.buildstash;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import java.util.HashMap;
import java.util.Map;

/**
 * Execution class for the Buildstash step.
 * Handles the actual upload process to the Buildstash service.
 */
public class BuildstashStepExecution extends SynchronousNonBlockingStepExecution<Map<String, Object>> {

    private static final long serialVersionUID = 1L;

    private final BuildstashStep step;

    public BuildstashStepExecution(BuildstashStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    protected Map<String, Object> run() throws Exception {
        TaskListener listener = getContext().get(TaskListener.class);
        FilePath workspace = getContext().get(FilePath.class);
        Run<?, ?> run = getContext().get(Run.class);
        EnvVars envFromContext = getContext().get(EnvVars.class);

        if (listener == null) {
            throw new IllegalStateException("TaskListener not available");
        }

        // Get environment variables for expansion
        // Always use run.getEnvironment() to ensure we have Git environment variables
        // (GIT_URL, GIT_BRANCH, GIT_COMMIT) that are set by the Git plugin
        EnvVars env;
        try {
            env = run.getEnvironment(listener);
            // Merge with context env vars if available (context might have step-specific vars)
            if (envFromContext != null) {
                env.overrideAll(envFromContext);
            }
        } catch (Exception e) {
            // If we can't get environment, use context env or create empty one
            env = envFromContext != null ? envFromContext : new EnvVars();
        }
        
        // Expand environment variables in all fields
        Secret apiKeySecret = step.getApiKey();
        String apiKeyPlain = apiKeySecret != null ? Secret.toString(apiKeySecret) : null;
        String expandedApiKey = BuildstashUploadHelper.expand(env, apiKeyPlain);
        String expandedStructure = BuildstashUploadHelper.expand(env, step.getStructure());
        // Ensure structure defaults to "file" if not set
        if (expandedStructure == null || expandedStructure.isBlank()) {
            expandedStructure = "file";
        }
        String expandedPrimaryFilePath = BuildstashUploadHelper.expand(env, step.getPrimaryFilePath());
        String expandedExpansionFilePath = BuildstashUploadHelper.expand(env, step.getExpansionFilePath());
        String expandedVersionComponent1Major = BuildstashUploadHelper.expand(env, step.getVersionComponent1Major());
        String expandedVersionComponent2Minor = BuildstashUploadHelper.expand(env, step.getVersionComponent2Minor());
        String expandedVersionComponent3Patch = BuildstashUploadHelper.expand(env, step.getVersionComponent3Patch());
        String expandedVersionComponentExtra = BuildstashUploadHelper.expand(env, step.getVersionComponentExtra());
        String expandedVersionComponentMeta = BuildstashUploadHelper.expand(env, step.getVersionComponentMeta());
        String expandedCustomBuildNumber = BuildstashUploadHelper.expand(env, step.getCustomBuildNumber());
        String expandedLabels = BuildstashUploadHelper.expand(env, step.getLabels());
        String expandedArchitectures = BuildstashUploadHelper.expand(env, step.getArchitectures());
        String expandedPlatform = BuildstashUploadHelper.expand(env, step.getPlatform());
        String expandedStream = BuildstashUploadHelper.expand(env, step.getStream());
        String expandedNotes = BuildstashUploadHelper.expand(env, step.getNotes());
        String expandedVcHostType = BuildstashUploadHelper.expand(env, step.getVcHostType());
        String expandedVcHost = BuildstashUploadHelper.expand(env, step.getVcHost());
        String expandedVcRepoName = BuildstashUploadHelper.expand(env, step.getVcRepoName());
        String expandedVcRepoUrl = BuildstashUploadHelper.expand(env, step.getVcRepoUrl());
        String expandedVcBranch = BuildstashUploadHelper.expand(env, step.getVcBranch());
        String expandedVcCommitSha = BuildstashUploadHelper.expand(env, step.getVcCommitSha());
        String expandedVcCommitUrl = BuildstashUploadHelper.expand(env, step.getVcCommitUrl());

        // Validate required parameters with expanded values
        BuildstashUploadHelper.validateParameters(expandedApiKey, expandedPrimaryFilePath, expandedVersionComponent1Major,
                expandedVersionComponent2Minor, expandedVersionComponent3Patch, expandedPlatform, expandedStream);

        // Create upload service
        BuildstashUploadService uploadService = new BuildstashUploadService(expandedApiKey, listener);

        // Prepare upload request with expanded values
        BuildstashUploadRequest request = BuildstashUploadHelper.createUploadRequest(workspace, run, expandedStructure,
                expandedPrimaryFilePath, expandedExpansionFilePath, expandedVersionComponent1Major,
                expandedVersionComponent2Minor, expandedVersionComponent3Patch, expandedVersionComponentExtra,
                expandedVersionComponentMeta, expandedCustomBuildNumber, expandedLabels, expandedArchitectures,
                expandedPlatform, expandedStream, expandedNotes, expandedVcHostType, expandedVcHost,
                expandedVcRepoName, expandedVcRepoUrl, expandedVcBranch, expandedVcCommitSha, expandedVcCommitUrl);
        
        // Auto-detect SCM info from BuildData (for pipelines)
        // This MUST be called after createUploadRequest so the request object is fully initialized
        VersionControlDetector.populateVersionControlInfo(run, request, listener);

        // Execute upload
        BuildstashUploadResponse response = uploadService.upload(request);

        // Log results
        BuildstashUploadHelper.logResults(listener, response);

        // Store results as build actions for later access (for UI display)
        run.addAction(new BuildstashBuildAction(response));

        // Return response as Map so it can be used in pipeline scripts without whitelisting
        Map<String, Object> result = new HashMap<>();
        result.put("buildId", response.getBuildId());
        result.put("buildInfoUrl", response.getBuildInfoUrl());
        result.put("downloadUrl", response.getDownloadUrl());
        result.put("pendingProcessing", response.isPendingProcessing());
        result.put("message", response.getMessage());
        return result;
    }

} 