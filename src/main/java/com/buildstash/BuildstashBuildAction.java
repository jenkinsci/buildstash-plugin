package com.buildstash;

import hudson.model.Action;
import hudson.model.Run;

/**
 * Build action to store Buildstash upload results.
 * This allows the upload results to be displayed on the build page.
 */
public class BuildstashBuildAction implements Action {

    private final BuildstashUploadResponse response;

    public BuildstashBuildAction(BuildstashUploadResponse response) {
        this.response = response;
    }

    @Override
    public String getIconFileName() {
        return "package.png";
    }

    @Override
    public String getDisplayName() {
        return "Buildstash Upload";
    }

    @Override
    public String getUrlName() {
        return "buildstash";
    }

    public BuildstashUploadResponse getResponse() {
        return response;
    }

    public String getBuildId() {
        return response != null ? response.getBuildId() : null;
    }

    public String getBuildInfoUrl() {
        return response != null ? response.getBuildInfoUrl() : null;
    }

    public String getDownloadUrl() {
        return response != null ? response.getDownloadUrl() : null;
    }

    public boolean isPendingProcessing() {
        return response != null && response.isPendingProcessing();
    }
} 