package com.buildstash;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class BuildstashBuilderTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testDescriptorProperties() {
        BuildstashBuilder.DescriptorImpl descriptor = new BuildstashBuilder.DescriptorImpl();
        
        assertEquals("Upload to Buildstash", descriptor.getDisplayName());
        assertTrue(descriptor.isApplicable(FreeStyleProject.class));
    }

    @Test
    public void testBuilderCreation() {
        BuildstashBuilder builder = new BuildstashBuilder();
        builder.setApiKey("test-key");
        builder.setPrimaryFilePath("test.apk");
        builder.setVersionComponent1Major("1");
        builder.setVersionComponent2Minor("0");
        builder.setVersionComponent3Patch("0");
        builder.setPlatform("android");
        builder.setStream("default");
        
        assertEquals("test-key", builder.getApiKey());
        assertEquals("test.apk", builder.getPrimaryFilePath());
        assertEquals("1", builder.getVersionComponent1Major());
        assertEquals("0", builder.getVersionComponent2Minor());
        assertEquals("0", builder.getVersionComponent3Patch());
        assertEquals("android", builder.getPlatform());
        assertEquals("default", builder.getStream());
    }

    @Test
    public void testDefaultValues() {
        BuildstashBuilder builder = new BuildstashBuilder();
        
        assertEquals("file", builder.getStructure());
        assertEquals("git", builder.getVcHostType());
        assertEquals("github", builder.getVcHost());
    }
}
