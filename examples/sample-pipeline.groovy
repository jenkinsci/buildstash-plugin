pipeline {
    agent any
    
    environment {
        BUILDSTASH_API_KEY = credentials('buildstash-api-key')
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build') {
            steps {
                echo 'Building application...'
                // Your build steps here
                sh 'echo "Building app..."'
                sh 'touch build/app.ipa' // Simulate build output
            }
        }
        
        stage('Upload to Buildstash') {
            steps {
                script {
                    def result = buildstash(
                        apiKey: BUILDSTASH_API_KEY,
                        structure: 'file',
                        primaryFilePath: 'build/app.ipa',
                        versionComponent1Major: '1',
                        versionComponent2Minor: '0',
                        versionComponent3Patch: '0',
                        versionComponentExtra: 'beta',
                        versionComponentMeta: "build.${env.BUILD_NUMBER}",
                        customBuildNumber: "${env.BUILD_NUMBER}",
                        labels: 'jenkins,automated,review',
                        architectures: 'arm64v8,armv9',
                        ciPipeline: 'Jenkins Pipeline',
                        ciRunId: env.BUILD_NUMBER,
                        ciRunUrl: env.BUILD_URL,
                        ciBuildDuration: currentBuild.duration.toString(),
                        vcHostType: 'git',
                        vcHost: 'github',
                        vcRepoName: env.GIT_REPO_NAME ?: 'my-app',
                        vcRepoUrl: env.GIT_URL ?: 'https://github.com/user/my-app',
                        vcBranch: env.GIT_BRANCH ?: 'main',
                        vcCommitSha: env.GIT_COMMIT ?: 'unknown',
                        vcCommitUrl: env.GIT_URL ? "${env.GIT_URL}/commit/${env.GIT_COMMIT}" : 'unknown',
                        platform: 'ios',
                        stream: 'development',
                        notes: "Built with Jenkins on ${env.NODE_NAME}"
                    )
                    
                    echo "Upload completed successfully!"
                    echo "Build ID: ${result.buildId}"
                    echo "Build Info URL: ${result.buildInfoUrl}"
                    echo "Download URL: ${result.downloadUrl}"
                    echo "Pending Processing: ${result.pendingProcessing}"
                    
                    // Store results for later use
                    env.BUILDSTASH_BUILD_ID = result.buildId
                    env.BUILDSTASH_DOWNLOAD_URL = result.downloadUrl
                }
            }
        }
        
        stage('Notify') {
            steps {
                echo "Build uploaded to Buildstash with ID: ${env.BUILDSTASH_BUILD_ID}"
                echo "Download URL: ${env.BUILDSTASH_DOWNLOAD_URL}"
            }
        }
    }
    
    post {
        always {
            echo 'Build completed'
        }
        success {
            echo "Successfully uploaded build ${env.BUILDSTASH_BUILD_ID} to Buildstash"
        }
        failure {
            echo 'Build failed'
        }
    }
} 