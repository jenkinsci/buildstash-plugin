package com.buildstash;

import hudson.Plugin;
import hudson.init.InitMilestone;
import hudson.init.Initializer;

/**
 * Main plugin class for Buildstash Jenkins Plugin.
 * This plugin allows uploading build artifacts to the Buildstash web service.
 */
public class BuildstashPlugin extends Plugin {

    /**
     * Plugin initialization.
     */
    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void init() {

    }
} 