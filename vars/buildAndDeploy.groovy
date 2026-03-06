#!/usr/bin/env groovy
/**
 * Main pipeline function for building and deploying applications
 * 
 * @param config Map containing pipeline configuration:
 *   - environment: 'dev' | 'staging' | 'prod' (required)
 *   - appName: Application name (required)
 *   - repo: Git repository URL (required)
 *   - branch: Git branch name (default: 'main')
 *   - dockerfile: Path to Dockerfile (default: './Dockerfile')
 *   - buildContext: Docker build context (default: '.')
 *   - deployment: Kubernetes deployment name (optional)
 *   - namespace: Kubernetes namespace (optional, defaults to environment)
 *   - registry: Docker registry URL (optional)
 *   - credentialsId: Jenkins credentials ID for Git (default: 'git-credentials')
 *   - dockerCredentialsId: Jenkins credentials ID for Docker (optional)
 *   - kubeContext: Kubernetes context name (optional)
 *   - envFile: Create .env file (boolean, default: false)
 *   - envContent: Content for .env file (string, optional)
 *   - preDeployScript: Script to run before deployment (string, optional)
 *   - postDeployScript: Script to run after deployment (string, optional)
 *   - skipDeploy: Skip deployment stage (boolean, default: false)
 *   - buildArgs: Docker build arguments (Map, optional)
 *   - skipTests: Skip test stage (boolean, default: true)
 */
def call(Map config) {
    // Load configuration
    def envConfig = new org.pipeline.utils.EnvironmentConfig().getConfig(config.environment)
    def pipelineConfig = buildPipelineConfig(config, envConfig)
    
    pipeline {
        agent any
        
        stages {
            stage('Validation') {
                steps {
                    script {
                        validateConfig(config)
                        
                        // Set environment variables dynamically
                        env.VERSION = pipelineConfig.version
                        env.IMAGE_NAME = pipelineConfig.imageName
                        env.FULL_IMAGE_NAME = pipelineConfig.fullImageName
                        env.APP_NAME = config.appName
                        env.ENVIRONMENT = config.environment
                        
                        echo "==================================="
                        echo "Pipeline Configuration:"
                        echo "App: ${config.appName}"
                        echo "Environment: ${config.environment}"
                        echo "Version: ${pipelineConfig.version}"
                        echo "Image: ${pipelineConfig.fullImageName}"
                        echo "==================================="
                    }
                }
            }
            
            stage('Confirmation') {
                when {
                    expression { return envConfig.requiresConfirmation }
                }
                steps {
                    script {
                        timeout(time: 10, unit: 'MINUTES') {
                            input message: "Deploy to ${config.environment.toUpperCase()}?",
                                  ok: "YES - Deploy to ${config.environment.toUpperCase()}"
                        }
                    }
                }
            }
            
            stage('Checkout') {
                steps {
                    script {
                        cleanWs()
                        checkoutCode(config)
                    }
                }
            }
            
            stage('Prepare Environment') {
                when {
                    expression { return config.envFile == true }
                }
                steps {
                    script {
                        if (config.envContent) {
                            def envPath = config.envFilePath ?: './.env'
                            writeFile file: envPath, text: config.envContent
                            echo "Environment file created at: ${envPath}"
                        }
                    }
                }
            }
            
            stage('Build Image') {
                steps {
                    script {
                        dockerBuild(config, pipelineConfig)
                    }
                }
            }
            
            stage('Push Image') {
                steps {
                    script {
                        dockerPushImage(config, pipelineConfig, envConfig)
                    }
                }
            }
            
            stage('Deploy') {
                when {
                    expression { return config.skipDeploy != true }
                }
                steps {
                    script {
                        deployApplication(config, pipelineConfig, envConfig)
                    }
                }
            }
            
            stage('Post Deploy Verification') {
                when {
                    expression { return config.skipDeploy != true && config.healthCheckUrl }
                }
                steps {
                    script {
                        healthCheck(config)
                    }
                }
            }
        }
        
        post {
            success {
                script {
                    echo "✅ ${config.appName} build and deployment completed successfully!"
                    echo "Version: ${pipelineConfig.version}"
                    echo "Environment: ${config.environment}"
                    
                    if (config.notificationWebhook) {
                        sendNotification(config, 'success', pipelineConfig)
                    }
                }
            }
            failure {
                script {
                    echo "❌ ${config.appName} build or deployment failed!"
                    
                    if (config.notificationWebhook) {
                        sendNotification(config, 'failure', pipelineConfig)
                    }
                }
            }
            always {
                script {
                    cleanup(config, pipelineConfig, envConfig)
                }
            }
        }
    }
}

def buildPipelineConfig(Map config, def envConfig) {
    def version = config.version ?: "v1.0.${env.BUILD_NUMBER}"
    def imageNameBase = config.imageName ?: config.appName
    
    // Build image name based on environment config
    def imageName = envConfig.imageNameTemplate ? 
        envConfig.imageNameTemplate.replace('{{appName}}', imageNameBase).replace('{{environment}}', config.environment) :
        "${imageNameBase}-${config.environment}"
    
    def registry = config.registry ?: envConfig.registry
    def fullImageName = "${registry}/${imageName}:${version}"
    
    return [
        version: version,
        imageName: imageName,
        fullImageName: fullImageName,
        registry: registry
    ]
}

def validateConfig(Map config) {
    // Validate required fields
    def required = ['environment', 'appName', 'repo']
    required.each { field ->
        if (!config[field]) {
            error "Missing required configuration: ${field}"
        }
    }
    
    // Validate environment
    def validEnvironments = ['dev', 'staging', 'prod']
    if (!(config.environment in validEnvironments)) {
        error "Invalid environment: ${config.environment}. Must be one of: ${validEnvironments.join(', ')}"
    }
    
    // Validate registry is provided (either in config or as parameter)
    def envConfig = new org.pipeline.utils.EnvironmentConfig().getConfig(config.environment)
    def registry = config.registry ?: envConfig.registry
    if (!registry) {
        error """
Missing required configuration: 'registry'

You must specify a Docker registry in your pipeline configuration:
  registry: 'your-registry.example.com'  // Or 'localhost:5000' for local dev

Examples:
  - Local registry: 'localhost:5000'
  - Docker Hub: 'your-dockerhub-username'
  - AWS ECR: '123456789.dkr.ecr.us-east-1.amazonaws.com'
  - GCR: 'gcr.io/your-project-id'
  - Custom: 'registry.example.com/project'
        """
    }
    
    // Validate kubeconfigPath for deployment
    if (config.skipDeploy != true) {
        def kubeconfigPath = config.kubeconfigPath ?: envConfig.kubeconfigPath
        if (!kubeconfigPath) {
            error """
Missing required configuration: 'kubeconfigPath'

You must specify the path to your Kubernetes config file:
  kubeconfigPath: '/path/to/your/kubeconfig'

Common examples:
  - Jenkins container: '/var/jenkins_home/.kube/config'
  - Local: '\$HOME/.kube/config' or '/root/.kube/config'
  - Custom path: '/path/to/custom-kubeconfig.yaml'

Tip: Mount your kubeconfig file into Jenkins container.
            """
        }
    }
}
