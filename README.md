# Jenkins Shared Pipeline Library

A generic, reusable Jenkins Shared Library for building and deploying applications to Kubernetes clusters or Docker environments.

## 🎯 Features

- ✅ **Multi-environment support** (dev, staging, prod)
- ✅ **Kubernetes deployment** (K3s, K8s, cloud providers)
- ✅ **Docker registry integration** (local registry, private registries)
- ✅ **Automatic versioning** with build numbers
- ✅ **Health checks** after deployment
- ✅ **Notifications** (Slack, Discord, Teams webhooks)
- ✅ **Deployment confirmation** for production
- ✅ **Automatic cleanup** of resources
- ✅ **Extensible and customizable**

## 📦 Installation

### 1. Setup in Jenkins

1. Go to **Manage Jenkins** → **Configure System**
2. Scroll to **Global Pipeline Libraries**
3. Click **Add**
4. Configure:
   - **Name**: `pipeline-library` (or any name you prefer)
   - **Default version**: `main` (or your default branch)
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: `https://github.com/YOUR-USERNAME/jenkins-shared-library`
   - **Credentials**: (Select your Git credentials if private repo)

### 2. Configure Credentials

Add these credentials in Jenkins (**Credentials** → **System** → **Global credentials**):

- **Git Credentials** (ID: `git-credentials`)
  - Type: Username with password or SSH key
  - For accessing your Git repositories

- **Docker Registry Credentials** (ID: `docker-registry-credentials`)
  - Type: Username with password
  - For pushing images to private registries

## 🚀 Quick Start

### Basic Usage

Create a new Pipeline job in Jenkins with this script:

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'dev',
    appName: 'my-app',
    repo: 'https://github.com/your-org/your-repo.git',
    branch: 'main'
)
```

### Full Example with All Options

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    // Required
    environment: 'prod',                    // dev, staging, or prod
    appName: 'my-api',                      // Application name
    repo: 'https://github.com/company/api.git',  // Git repository
    
    // Optional - Git
    branch: 'main',                         // Default: 'main'
    credentialsId: 'git-credentials',       // Default: 'git-credentials'
    
    // Optional - Docker Build
    dockerfile: './Dockerfile',             // Default: './Dockerfile'
    buildContext: '.',                      // Default: '.'
    platform: 'linux/amd64',                // Default: 'linux/amd64'
    buildArgs: [                            // Additional build arguments
        NODE_ENV: 'production',
        API_VERSION: '2.0'
    ],
    
    // Optional - Docker Registry
    registry: 'registry.example.com',       // Override environment default
    dockerCredentialsId: 'docker-creds',    // Override environment default
    tagLatest: true,                        // Also tag as 'latest'
    
    // Optional - Versioning
    version: 'v2.0.1',                      // Override auto-generated version
    
    // Optional - Kubernetes Deployment
    deploymentType: 'kubernetes',           // kubernetes, docker, docker-compose
    deployment: 'my-api-deployment',        // K8s deployment name
    namespace: 'production',                // Override environment namespace
    kubeContext: 'prod-cluster',            // Override environment context
    containerName: 'api',                   // Container name in pod
    setImage: true,                         // Update image before rollout
    waitForRollout: true,                   // Wait for rollout to complete
    rolloutTimeout: '10m',                  // Rollout timeout
    
    // Optional - Health Check
    healthCheckUrl: 'https://api.example.com/health',
    healthCheckRetries: 10,                 // Number of retries
    healthCheckDelay: 10,                   // Delay between retries (seconds)
    healthCheckExpectedStatus: 200,         // Expected HTTP status
    
    // Optional - Scripts
    preDeployScript: '''
        echo "Running pre-deployment checks..."
        # Your custom script here
    ''',
    postDeployScript: '''
        echo "Running post-deployment tasks..."
        # Your custom script here
    ''',
    
    // Optional - Environment File
    envFile: true,                          // Create .env file
    envFilePath: './.env',                  // Path to .env file
    envContent: '''
        NODE_ENV=production
        DATABASE_URL=postgres://...
        API_KEY=your-api-key
    ''',
    
    // Optional - Notifications
    notificationWebhook: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL',
    
    // Optional - Cleanup
    cleanupImages: true,                    // Remove local images after push
    cleanWorkspace: true,                   // Clean workspace after build
    
    // Optional - Control
    skipDeploy: false,                      // Skip deployment stage
    skipTests: true                         // Skip test stage
)
```

## 📋 Environment Configuration

The library comes with three pre-configured environments that you can customize:

### Development (dev)
- **Registry**: `localhost:5000`
- **Namespace**: `dev`
- **Context**: `dev`
- **Registry Login**: Not required
- **Confirmation**: Not required
- **Cleanup**: Disabled

### Staging
- **Registry**: `registry.example.com`
- **Namespace**: `staging`
- **Context**: `staging`
- **Registry Login**: Required
- **Confirmation**: Not required
- **Cleanup**: Enabled

### Production (prod)
- **Registry**: `registry.example.com`
- **Namespace**: `production`
- **Context**: `production`
- **Registry Login**: Required
- **Confirmation**: Required ✅
- **Cleanup**: Enabled

## 🔧 Customization

### Customize Environment Configuration

Edit `src/org/pipeline/utils/EnvironmentConfig.groovy`:

```groovy
'prod': [
    registry: 'your-registry.example.com',
    namespace: 'production',
    kubeContext: 'prod-cluster',
    kubeconfigPath: '/var/jenkins_home/.kube/config',
    requiresRegistryLogin: true,
    dockerCredentialsId: 'your-docker-credentials',
    requiresConfirmation: true,
    cleanupImages: true,
    imageNameTemplate: '{{appName}}-prod'
]
```

### Add New Environment

Add a new environment in `EnvironmentConfig.groovy`:

```groovy
'uat': [
    registry: 'uat-registry.example.com',
    namespace: 'uat',
    kubeContext: 'uat',
    requiresRegistryLogin: true,
    requiresConfirmation: false,
    cleanupImages: true
]
```

## 📚 Real-World Examples

### Example 1: Simple NodeJS API (Dev)

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'dev',
    appName: 'nodejs-api',
    repo: 'https://github.com/company/nodejs-api.git',
    branch: 'develop',
    deployment: 'nodejs-api'
)
```

### Example 2: React Frontend with Environment Variables

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'react-app',
    repo: 'https://github.com/company/react-app.git',
    branch: 'main',
    
    envFile: true,
    envContent: '''
        REACT_APP_API_URL=https://api.example.com
        REACT_APP_ENV=production
        REACT_APP_VERSION=1.0.0
    ''',
    
    healthCheckUrl: 'https://app.example.com',
    notificationWebhook: 'https://hooks.slack.com/...'
)
```

### Example 3: Microservice with Custom Dockerfile

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'payment-service',
    repo: 'https://github.com/company/payment-service.git',
    branch: 'release',
    
    dockerfile: './docker/Dockerfile.prod',
    buildContext: './services/payment',
    buildArgs: [
        SERVICE_VERSION: '2.1.0',
        BUILD_ENV: 'production'
    ],
    
    deployment: 'payment-service',
    namespace: 'production',
    containerName: 'payment',
    
    healthCheckUrl: 'https://payment.example.com/health',
    healthCheckRetries: 20,
    healthCheckDelay: 5
)
```

### Example 4: With Pre/Post Deploy Scripts

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'database-api',
    repo: 'https://github.com/company/database-api.git',
    
    preDeployScript: '''
        echo "Running database migrations..."
        kubectl exec -it migration-pod -- npm run migrate
    ''',
    
    postDeployScript: '''
        echo "Warming up cache..."
        curl -X POST https://api.example.com/admin/cache/warm
    ''',
    
    healthCheckUrl: 'https://api.example.com/health'
)
```

## 🏗️ Project Structure

```
jenkins-shared-library/
├── vars/                           # Pipeline steps (callable from Jenkinsfile)
│   ├── buildAndDeploy.groovy      # Main entry point
│   ├── checkoutCode.groovy        # Git checkout
│   ├── dockerBuild.groovy         # Docker build
│   ├── dockerPushImage.groovy     # Docker push
│   ├── deployApplication.groovy   # Deployment orchestration
│   ├── kubernetesRollout.groovy   # K8s rollout restart
│   ├── healthCheck.groovy         # Health check verification
│   ├── sendNotification.groovy    # Send notifications
│   └── cleanup.groovy             # Cleanup resources
│
├── src/org/pipeline/              # Groovy classes
│   ├── deployers/
│   │   └── KubernetesDeployer.groovy
│   ├── registries/
│   │   └── RegistryHandler.groovy
│   └── utils/
│       ├── EnvironmentConfig.groovy
│       └── PipelineUtils.groovy
│
└── resources/                      # Static resources
    ├── scripts/
    └── configs/
```

## 🔐 Security Best Practices

1. **Never hardcode credentials** - Always use Jenkins credentials
2. **Use private registries** for production images
3. **Enable deployment confirmation** for production
4. **Implement RBAC** in Kubernetes
5. **Use secrets** for sensitive environment variables
6. **Enable image scanning** before deployment

## 🐛 Troubleshooting

### Issue: "kubectl: command not found"

**Solution**: Install kubectl in Jenkins agent or use a Docker agent with kubectl pre-installed.

### Issue: "Cannot connect to Docker daemon"

**Solution**: Ensure Jenkins user has access to Docker socket or use Docker-in-Docker.

### Issue: "Deployment not found"

**Solution**: Set `createIfNotExists: true` and provide `manifestPath` to create deployment.

### Issue: "Health check failed"

**Solution**: Increase `healthCheckRetries` and `healthCheckDelay`, or check if your health endpoint is correct.

## 📝 License

MIT License - Feel free to use and modify for your projects.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📞 Support

For issues and questions, please open an issue in this repository.

---

**Made with ❤️ for the DevOps community**
