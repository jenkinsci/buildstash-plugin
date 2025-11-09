# Buildstash

<img src="https://media.buildstash.com/logos/promo1.png" alt="Buildstash - A new home for your software binaries" style="max-width: 600px; width: 100%;">

A Jenkins plugin for uploading build artifacts to [Buildstash](https://buildstash.com) - for management and organization of binaries, sharing with your team and collaborators, and deployment. This plugin provides both Freestyle and Pipeline steps that can be used in Jenkins projects to upload files to Buildstash, supporting both direct file uploads and chunked uploads for large files.

## Features

- **Pipeline Step**: Easy-to-use pipeline step for uploading build artifacts
- **Freestyle Support**: Build step for classic Jenkins Freestyle projects
- **Chunked Uploads**: Support for large file uploads using multipart uploads
- **Pipeline and SCM Metadata**: Automatically stores associated CI/CD and SCM context with the build
- **Multiple Platforms**: Support for a wide array of platforms (Windows, macOS, Linux, iOS, Android, game consoles, and many others)
- **Binary Organization and Distribution**: Once uploaded to Buildstash you have powerful controls over organizing your binaries, sharing with collaborators and testers, and distributing to users

## Installation

### Prerequisites

- Jenkins 2.479 or later
- Java 17 or later

## Usage

### Dynamic Values with Environment Variables

All fields support dynamic values using environment variables. This allows you to set values from previous build steps or use Jenkins environment variables.

**For Freestyle Projects:**
- Use `${VAR_NAME}` syntax in form fields to reference environment variables
- Set environment variables in earlier build steps (e.g., shell scripts)
- Example: Set `platform` field to `${PLATFORM}` where `PLATFORM` is set in a previous step

**For Pipeline Projects:**
- Use Groovy variables directly: `platform: env.PLATFORM`
- Or use string expansion: `platform: '${PLATFORM}'`
- Example: `platform: "${env.BUILD_TYPE}"` or `platform: env.BUILD_TYPE`

All fields support this, including:
- File paths: `primaryFilePath: '${WORKSPACE}/build/app.ipa'`
- Version components: `versionComponent1Major: '${MAJOR_VERSION}'`
- Platform, stream, labels, architectures, etc

### Setting Up Credentials

Before using the plugin, you should store your Buildstash API key as a Jenkins credential:

1. Go to **Manage Jenkins** → **Manage Credentials**
2. Select the domain (usually "Global")
3. Click **Add Credentials**
4. Choose **Secret text** as the kind
5. Enter:
   - **Secret**: Your Buildstash API key
   - **ID**: `buildstash-api-key` (or any ID you prefer)
   - **Description**: "Buildstash API Key"
6. Click **OK**

Bear in mind pipeline API keys are application specific, so you may wish to include the name of your application in the credential ID to differentiate.

### Pipeline Step

The plugin provides a `buildstash` step that can be used in Jenkins pipelines:

```groovy
pipeline {
    agent any
    
    environment {
        // Other environment variables
        PLATFORM = 'android'
        STREAM = 'stable'
    }
    
    stages {
        stage('Build') {
            steps {
                // Your build steps here
                sh 'make build'
            }
        }
        
        stage('Upload to Buildstash') {
            steps {
                // Use withCredentials to safely load API key
                // 'buildstash-api-key' is the ID of the credential you created
                withCredentials([string(credentialsId: 'buildstash-api-key', variable: 'BUILDSTASH_API_KEY')]) {
                    buildstash(
                        apiKey: env.BUILDSTASH_API_KEY,  // Using credential (automatically masked in logs)
                        structure: 'file',
                        primaryFilePath: 'build/app.apk',
                        versionComponent1Major: '1',
                        versionComponent2Minor: '0',
                        versionComponent3Patch: '0',
                        platform: env.PLATFORM,  // Using environment variable
                        stream: env.STREAM,        // Using environment variable
                        labels: 'automated,signed,to-review',
                        architectures: 'arm64v8,armv9'
                    )
                }
            }
        }
    }
}
```

### Freestyle Project

The plugin also provides a build step for classic Jenkins Freestyle projects:

1. **Create a Freestyle Project**: Go to Jenkins and create a new Freestyle project
2. **Add Build Steps**: Configure your build steps (compile, test, etc.)
3. **Set Environment Variables** (optional): If you'd like to pass values into the Buildstash step dynamically, you can do this via environment variables:
   ```bash
   export PLATFORM=ios
   export STREAM=development
   export BUILD_PATH=build/app.ipa
   ```
4. **Add Buildstash Step**: In your post-build actions, add "Upload to Buildstash"
5. **Configure Parameters**: Fill in the required parameters:
   - API Key
   - Primary File Path (can use `${BUILD_PATH}` to reference environment variable)
   - Semantic version components (Major, Minor, Patch)
   - Platform (i.e. could use `${PLATFORM}` to reference environment variable)
   - Stream
6. **Save and Run**: Save the project configuration and run the build

**Using Environment Variables in Freestyle:**
- Use `${VAR_NAME}` syntax in any form field
- Variables are expanded at runtime from the build environment
- Example: Set Platform field to `${PLATFORM}` where `PLATFORM` was set in a previous build step

After a successful upload, the build results will be displayed on the build page with links to:
- Build ID
- Build Info URL
- Download URL
- Processing status (applies to iOS or Android builds which require additional processing on the server to make available for beta distribution)

### File + Expansion Upload

You can optionally provide an additional expansion file for platforms that require it (e.g. Android):

```groovy
pipeline {
    agent any
    
    stages {
        stage('Upload to Buildstash') {
            steps {
                withCredentials([string(credentialsId: 'buildstash-api-key', variable: 'BUILDSTASH_API_KEY')]) {
                    buildstash(
                        apiKey: env.BUILDSTASH_API_KEY,
                        structure: 'file+expansion',
                        primaryFilePath: 'build/app.apk',
                        expansionFilePath: 'build/app.obb',
                        versionComponent1Major: '1',
                        versionComponent2Minor: '0',
                        versionComponent3Patch: '0',
                        platform: 'android',
                        stream: 'nightlies'
                    )
                }
            }
        }
    }
}
```

### Advanced Configuration

Complete example with all available parameters:

```groovy
pipeline {
    agent any
    
    environment {
        PLATFORM = 'windows'
        STREAM = 'production'
    }
    
    stages {
        stage('Upload to Buildstash') {
            steps {
                withCredentials([string(credentialsId: 'buildstash-api-key', variable: 'BUILDSTASH_API_KEY')]) {
                    buildstash(
                        // Required parameters
                        apiKey: env.BUILDSTASH_API_KEY,
                        primaryFilePath: "${env.WORKSPACE}/build/app.exe",
                        versionComponent1Major: '2',
                        versionComponent2Minor: '5',
                        versionComponent3Patch: '1',
                        platform: env.PLATFORM,
                        stream: env.STREAM,
                        
                        // Optional version components
                        versionComponentExtra: 'beta',
                        versionComponentMeta: '2024.12.15',
                        customBuildNumber: "${env.BUILD_NUMBER}-release",
                        
                        // Optional labels and architectures
                        labels: 'production,release,signed',
                        architectures: 'arm64v8,armv9',
                        
                        // Optional SCM fields (usually auto-detected if not provided)
                        vcHostType: 'git',
                        vcHost: 'github',
                        vcRepoName: 'my-awesome-app',
                        vcRepoUrl: 'https://github.com/user/my-awesome-app',
                        vcBranch: env.GIT_BRANCH ?: 'main',
                        vcCommitSha: env.GIT_COMMIT ?: 'abc123def456',
                        vcCommitUrl: "https://github.com/user/my-awesome-app/commit/${env.GIT_COMMIT ?: 'abc123def456'}",
                        
                        // Optional build notes
                        notes: "Built on ${env.NODE_NAME}"
                    )
                }
            }
        }
    }
}
```

**Note:** CI fields (`ciPipeline`, `ciPipelineUrl`, `ciRunId`, `ciRunUrl`, `ciBuildDuration`) are automatically populated from Jenkins context, so you don't need to pass them explicitly.

## Parameters

> **Note:** All parameters support dynamic values using environment variables. In Freestyle projects, use `${VAR_NAME}` syntax. In Pipeline scripts, use Groovy variables like `env.VAR_NAME` or string expansion `'${VAR_NAME}'`.

### Required Parameters

| Parameter | Description | Example                                                 |
|-----------|-------------|---------------------------------------------------------|
| `apiKey` | Your Buildstash API key (use Jenkins Credentials with `withCredentials`) | `env.BUILDSTASH_API_KEY` (when using `withCredentials`) |
| `primaryFilePath` | Path to the primary file to upload | `'build/app.dmg'`                                       |
| `versionComponent1Major` | Major version component | `'1'`                                                   |
| `versionComponent2Minor` | Minor version component | `'0'`                                                   |
| `versionComponent3Patch` | Patch version component | `'0'`                                                   |
| `platform` | Target platform | `'macos'`, `'android'`, etc.                            |
| `stream` | Build stream | `'stable'`, `'nightlies'`, etc.                         |

### Optional Parameters

| Parameter | Description                     | Default | Example                                          |
|-----------|---------------------------------|---------|--------------------------------------------------|
| `structure` | Upload structure type           | `'file'` | `'file'`, `'file+expansion'`                     |
| `expansionFilePath` | Path to expansion file          | `null` | `'build/app-obb.zip'`                            |
| `versionComponentExtra` | Extra version component         | `null` | `'beta'`                                         |
| `versionComponentMeta` | Meta version component          | `null` | `'build.123'`                                    |
| `customBuildNumber` | Custom build number             | `null` | `'2023.12.01'`                                   |
| `labels` | Labels (comma-separated)        | `null` | `'jenkins,to-review'`                            |
| `architectures` | Architectures (comma-separated) | `null` | `'arm64v8,armv9'`                                |
| `vcHostType` | Version control host type       | `'git'` | `'git'`                                          |
| `vcHost` | Version control host            | `'github'` | `'github'`, `'gitlab'`                           |
| `vcRepoName` | Repository name                 | `null` | `'my-app'`                                       |
| `vcRepoUrl` | Repository URL                  | `null` | `'https://github.com/user/my-app'`               |
| `vcBranch` | Branch name                     | `null` | `env.GIT_BRANCH`                                 |
| `vcCommitSha` | Commit SHA                      | `null` | `env.GIT_COMMIT`                                 |
| `vcCommitUrl` | Commit URL                      | `null` | `'https://github.com/user/my-app/commit/abc123'` |
| `notes` | Build notes                     | `null` | `'Built with Jenkins'`                           |

## Outputs

The step provides the following outputs that can be accessed in subsequent pipeline steps:

- `buildId`: The ID of the uploaded build
- `pendingProcessing`: Whether the build is pending additional processing before being available for download (for iOS or Android only, usually takes a few minutes)
- `buildInfoUrl`: URL to view the build information on Buildstash
- `downloadUrl`: URL to download the build

Example usage of outputs:

```groovy
def result = buildstash(
    apiKey: env.BUILDSTASH_API_KEY,
    // ... other parameters
)

echo "Build ID: ${result.buildId}"
echo "Build Info URL: ${result.buildInfoUrl}"
echo "Download URL: ${result.downloadUrl}"
echo "Pending Processing: ${result.pendingProcessing}"
```

**Note:** The result is returned as a Map, so you can also access values using bracket notation: `result['buildId']` or `result.buildId` (both work in Groovy).

## Supported Platforms

Buildstash supports uploading and managing software binaries for a wide array of platforms. See a [full list of supported platforms in the Buildstash docs](https://docs.buildstash.com/data/platforms).

## File Size Limits

File size limits for uploaded artifacts may vary by platform or your Buildstash plan. All platforms support at least 5GB, and some up to 150GB. See [buildstash.com/pricing](https://buildstash.com/pricing) for more info.

## License

This project is licensed under the [MIT License](LICENSE).

## More info

- **Issues**: [GitHub Issues](https://github.com/jenkinsci/buildstash-plugin/issues)
- **Support**: https://support.buildstash.com
- **Documentation**: [Buildstash Platform Docs](https://support.buildstash.com/docs)
- **Buildstash Website**: [Buildstash](https://buildstash.com)