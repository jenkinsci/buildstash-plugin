# Buildstash Jenkins Plugin

A Jenkins plugin for uploading build artifacts to the [Buildstash](https://buildstash.com) web service. This plugin provides a pipeline step that can be used in Jenkins pipelines to upload files to Buildstash, supporting both direct file uploads and chunked uploads for large files.

## Features

- **Pipeline Step**: Easy-to-use pipeline step for uploading build artifacts
- **Freestyle Support**: Build step for classic Jenkins Freestyle projects
- **Chunked Uploads**: Support for large file uploads using chunked multipart uploads
- **Direct Uploads**: Simple direct file uploads for smaller files
- **File + Expansion**: Support for uploading primary files with expansion files
- **Version Control Integration**: Automatic integration with Git repositories
- **CI/CD Metadata**: Comprehensive build metadata collection
- **Multiple Platforms**: Support for various platforms (iOS, Android, etc.)

## Installation

### Prerequisites

- Jenkins 2.387.3 or later
- Java 11 or later

### Manual Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Install the generated `.hpi` file in Jenkins:
   - Go to **Manage Jenkins** > **Manage Plugins** > **Advanced**
   - Upload the `.hpi` file in the **Upload Plugin** section
   - Restart Jenkins

### Development Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/jenkinsci/buildstash-plugin.git
   cd buildstash-plugin
   ```

2. Run Jenkins with the plugin:
   ```bash
   mvn hpi:run
   ```

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

All fields support this feature, including:
- File paths: `primaryFilePath: '${WORKSPACE}/build/app.ipa'`
- Version components: `versionComponent1Major: '${MAJOR_VERSION}'`
- Platform, Stream, Labels, Architectures
- SCM fields, Notes, Custom Build Number

### Setting Up Credentials

Before using the plugin, you need to store your Buildstash API key as a Jenkins credential:

1. Go to **Manage Jenkins** → **Manage Credentials**
2. Select the domain (usually "Global")
3. Click **Add Credentials**
4. Choose **Secret text** as the kind
5. Enter:
   - **Secret**: Your Buildstash API key
   - **ID**: `buildstash-api-key` (or any ID you prefer)
   - **Description**: "Buildstash API Key"
6. Click **OK**

### Pipeline Step

The plugin provides a `buildstash` step that can be used in Jenkins pipelines:

```groovy
pipeline {
    agent any
    
    environment {
        // Other environment variables
        PLATFORM = 'ios'
        STREAM = 'development'
        BUILD_VERSION = '1.0.0'
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
                        primaryFilePath: 'build/app.ipa',
                        versionComponent1Major: '1',
                        versionComponent2Minor: '0',
                        versionComponent3Patch: '0',
                        platform: env.PLATFORM,  // Using environment variable
                        stream: env.STREAM,        // Using environment variable
                        labels: 'beta\ntest',
                        architectures: 'arm64\nx86_64'
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
3. **Set Environment Variables** (optional): In a shell script step, set variables:
   ```bash
   export PLATFORM=ios
   export STREAM=development
   export BUILD_PATH=build/app.ipa
   ```
4. **Add Buildstash Step**: Add a new build step and select "Upload to Buildstash"
5. **Configure Parameters**: Fill in the required parameters:
   - API Key
   - Primary File Path (can use `${BUILD_PATH}` to reference environment variable)
   - Version components (Major, Minor, Patch)
   - Platform (can use `${PLATFORM}` to reference environment variable)
   - Stream (can use `${STREAM}` to reference environment variable)
6. **Save and Run**: Save the project configuration and run the build

**Using Environment Variables in Freestyle:**
- Use `${VAR_NAME}` syntax in any form field
- Variables are expanded at runtime from the build environment
- Example: Set Platform field to `${PLATFORM}` where `PLATFORM` was set in a previous build step

**Using Credentials in Freestyle:**
1. Install the **Credentials Binding Plugin** if not already installed
2. Add build step: **Inject environment variables to the build process**
3. In "Credentials" section, bind your credential (e.g., `buildstash-api-key`) to an environment variable (e.g., `BUILDSTASH_API_KEY`)
4. In the Buildstash step, set API Key field to: `${BUILDSTASH_API_KEY}`

The build step will appear in the "Build" section of your Freestyle project configuration, and you can configure all the same parameters as the pipeline step.

After a successful upload, the build results will be displayed on the build page with links to:
- Build ID
- Build Info URL
- Download URL
- Processing status

### File + Expansion Upload

For platforms that require both a primary file and an expansion file:

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
                        primaryFilePath: 'build/app.aab',
                        expansionFilePath: 'build/app-obb.zip',
                        versionComponent1Major: '1',
                        versionComponent2Minor: '0',
                        versionComponent3Patch: '0',
                        platform: 'android',
                        stream: 'production'
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
        PLATFORM = 'ios'
        STREAM = 'production'
    }
    
    stages {
        stage('Upload to Buildstash') {
            steps {
                withCredentials([string(credentialsId: 'buildstash-api-key', variable: 'BUILDSTASH_API_KEY')]) {
                    buildstash(
                        // Required parameters
                        apiKey: env.BUILDSTASH_API_KEY,
                        primaryFilePath: "${env.WORKSPACE}/build/app.ipa",
                        versionComponent1Major: '2',
                        versionComponent2Minor: '5',
                        versionComponent3Patch: '1',
                        platform: env.PLATFORM,
                        stream: env.STREAM,
                        
                        // Optional structure and expansion
                        structure: 'file+expansion',
                        expansionFilePath: "${env.WORKSPACE}/build/app.obb",
                        
                        // Optional version components
                        versionComponentExtra: 'beta',
                        versionComponentMeta: '2024.12.15',
                        customBuildNumber: "${env.BUILD_NUMBER}-release",
                        
                        // Optional labels and architectures
                        labels: 'production,release,signed',
                        architectures: 'arm64v8,armv9',
                        
                        // Optional SCM fields (auto-detected if not provided)
                        vcHostType: 'git',
                        vcHost: 'github',
                        vcRepoName: 'my-awesome-app',
                        vcRepoUrl: 'https://github.com/user/my-awesome-app',
                        vcBranch: env.GIT_BRANCH ?: 'main',
                        vcCommitSha: env.GIT_COMMIT ?: 'abc123def456',
                        vcCommitUrl: "https://github.com/user/my-awesome-app/commit/${env.GIT_COMMIT ?: 'abc123def456'}",
                        
                        // Optional notes
                        notes: "Built on ${env.NODE_NAME}"
                    )
                }
            }
        }
    }
}
```

**Note:** CI fields (`ciPipeline`, `ciRunId`, `ciRunUrl`, `ciPipelineUrl`, `ciBuildDuration`) are automatically populated from Jenkins context, so you don't need to pass them explicitly.

## Parameters

> **Note:** All parameters support dynamic values using environment variables. In Freestyle projects, use `${VAR_NAME}` syntax. In Pipeline scripts, use Groovy variables like `env.VAR_NAME` or string expansion `'${VAR_NAME}'`.

### Required Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `apiKey` | Your Buildstash API key (use Jenkins Credentials with `withCredentials`) | `env.BUILDSTASH_API_KEY` (when using `withCredentials`) |
| `primaryFilePath` | Path to the primary file to upload | `'build/app.ipa'` |
| `versionComponent1Major` | Major version component | `'1'` |
| `versionComponent2Minor` | Minor version component | `'0'` |
| `versionComponent3Patch` | Patch version component | `'0'` |
| `platform` | Target platform | `'ios'`, `'android'`, etc. |
| `stream` | Build stream | `'development'`, `'production'`, etc. |

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
| `ciPipeline` | CI pipeline name                | `null` | `'Jenkins Pipeline'`                             |
| `ciRunId` | CI run ID                       | `null` | `env.BUILD_NUMBER`                               |
| `ciRunUrl` | CI run URL                      | `null` | `env.BUILD_URL`                                  |
| `ciBuildDuration` | CI build duration               | `null` | `currentBuild.duration.toString()`               |
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
- `pendingProcessing`: Whether the build is pending additional processing
- `buildInfoUrl`: URL to view the build information on Buildstash
- `downloadUrl`: URL to download the build

Example usage of outputs:

```groovy
def result = buildstash(
    apiKey: 'your-api-key',
    // ... other parameters
)

echo "Build ID: ${result.buildId}"
echo "Build Info URL: ${result.buildInfoUrl}"
echo "Download URL: ${result.downloadUrl}"
```

## Supported Platforms

The plugin supports various platforms including:

- **iOS**: `.ipa`, `.app` files
- **Android**: `.apk`, `.aab` files
- **macOS**: `.dmg`, `.pkg` files
- **Windows**: `.exe`, `.msi` files
- **Linux**: Various package formats
- **Web**: Web application bundles

## File Size Limits

- **Direct Upload**: Files up to 5GB
- **Chunked Upload**: Files larger than 5GB (automatically handled)

## Error Handling

The plugin provides comprehensive error handling:

- **File Not Found**: Validates that specified files exist before upload
- **Network Errors**: Retries failed uploads with exponential backoff
- **API Errors**: Detailed error messages from the Buildstash API
- **Validation Errors**: Parameter validation with helpful error messages

## Security

- API keys are masked in Jenkins logs
- HTTPS communication with Buildstash API
- No sensitive data is stored in Jenkins

## Development

### Building

```bash
mvn clean package
```

### Testing

```bash
mvn test
```

### Running Tests with Jenkins

```bash
mvn hpi:run
```

### Code Style

The project follows standard Java conventions and uses:

- Java 11
- Maven for build management
- JUnit for testing
- Jenkins plugin parent POM

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support and questions:

- **Documentation**: [Buildstash Documentation](https://docs.buildstash.com)
- **Issues**: [GitHub Issues](https://github.com/jenkinsci/buildstash-plugin/issues)
- **Email**: support@buildstash.com

## Changelog

### Version 1.0.0
- Initial release
- Support for direct and chunked file uploads
- Pipeline step integration
- Comprehensive metadata collection
- Version control integration