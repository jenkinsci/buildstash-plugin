#!/bin/bash

# Buildstash Jenkins Plugin Build Script

set -e

echo "Building Buildstash Jenkins Plugin..."

# Clean previous builds
echo "Cleaning previous builds..."
mvn clean

# Run tests
echo "Running tests..."
mvn test

# Build the plugin
echo "Building plugin..."
mvn package

echo "Build completed successfully!"
echo "Plugin file: target/buildstash.hpi"

# Check if we should run Jenkins with the plugin
if [ "$1" = "--run" ]; then
    echo "Starting Jenkins with plugin..."
    mvn hpi:run
fi

echo "Done!" 