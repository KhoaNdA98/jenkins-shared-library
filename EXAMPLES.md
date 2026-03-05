# Jenkins Shared Library Examples

This directory contains example Jenkinsfiles demonstrating various use cases of the pipeline library.

## 📁 Example Configurations

### 1. Simple Development Deployment

**Use Case**: Quick deployment to dev environment with minimal configuration

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'dev',
    appName: 'simple-app',
    repo: 'https://github.com/yourcompany/simple-app.git'
)
```

### 2. Production Deployment with All Features

**Use Case**: Full-featured production deployment with health checks and notifications

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'production-api',
    repo: 'https://github.com/yourcompany/production-api.git',
    branch: 'main',
    
    // Docker configuration
    dockerfile: './Dockerfile.prod',
    buildContext: '.',
    buildArgs: [
        NODE_ENV: 'production',
        API_VERSION: '2.0'
    ],
    
    // Kubernetes deployment
    deployment: 'production-api',
    namespace: 'production',
    containerName: 'api',
    waitForRollout: true,
    rolloutTimeout: '10m',
    
    // Health check
    healthCheckUrl: 'https://api.example.com/health',
    healthCheckRetries: 15,
    healthCheckDelay: 10,
    
    // Notifications
    notificationWebhook: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
)
```

### 3. Microservice with Environment Variables

**Use Case**: Deploy microservice with runtime configuration

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'user-service',
    repo: 'https://github.com/yourcompany/user-service.git',
    
    envFile: true,
    envContent: '''
        DATABASE_URL=postgres://prod-db:5432/users
        REDIS_URL=redis://prod-cache:6379
        JWT_SECRET=your-secret-key
        LOG_LEVEL=info
    ''',
    
    deployment: 'user-service',
    healthCheckUrl: 'https://users.example.com/health'
)
```

### 4. Frontend Application (React/Vue/Angular)

**Use Case**: Build and deploy frontend application with build-time env vars

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'webapp',
    repo: 'https://github.com/yourcompany/webapp.git',
    branch: 'main',
    
    dockerfile: './Dockerfile',
    buildArgs: [
        REACT_APP_API_URL: 'https://api.example.com',
        REACT_APP_ENV: 'production',
        REACT_APP_GA_ID: 'UA-XXXXX-Y'
    ],
    
    deployment: 'webapp',
    healthCheckUrl: 'https://app.example.com',
    
    notificationWebhook: 'https://hooks.slack.com/services/...'
)
```

### 5. Multi-Stage Build with Tests

**Use Case**: Build with testing stage before deployment

```groovy
@Library('pipeline-library') _

pipeline {
    agent any
    
    stages {
        stage('Test') {
            steps {
                script {
                    sh 'npm install'
                    sh 'npm test'
                    sh 'npm run lint'
                }
            }
        }
        
        stage('Build & Deploy') {
            steps {
                script {
                    buildAndDeploy(
                        environment: 'prod',
                        appName: 'tested-app',
                        repo: 'https://github.com/yourcompany/tested-app.git',
                        deployment: 'tested-app'
                    )
                }
            }
        }
    }
}
```

### 6. Database Service with Migration

**Use Case**: Deploy database-dependent service with pre-deployment migrations

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'api-with-db',
    repo: 'https://github.com/yourcompany/api-with-db.git',
    
    preDeployScript: '''
        echo "Running database migrations..."
        kubectl exec -n production deployment/migration-runner -- npm run migrate
        echo "Migrations completed"
    ''',
    
    deployment: 'api-with-db',
    healthCheckUrl: 'https://api.example.com/health',
    
    postDeployScript: '''
        echo "Seeding cache..."
        curl -X POST https://api.example.com/admin/cache/seed
    '''
)
```

### 7. Monorepo - Multiple Services

**Use Case**: Deploy specific service from monorepo

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'payment-service',
    repo: 'https://github.com/yourcompany/monorepo.git',
    
    dockerfile: './services/payment/Dockerfile',
    buildContext: './services/payment',
    
    deployment: 'payment-service',
    namespace: 'production',
    containerName: 'payment',
    
    healthCheckUrl: 'https://payment.example.com/health'
)
```

### 8. Private Registry with Custom Credentials

**Use Case**: Use custom Docker registry and credentials

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'prod',
    appName: 'private-app',
    repo: 'https://github.com/yourcompany/private-app.git',
    
    registry: 'private-registry.company.com/apps',
    dockerCredentialsId: 'private-registry-credentials',
    
    deployment: 'private-app',
    healthCheckUrl: 'https://app.company.com/health'
)
```

### 9. Conditional Deployment

**Use Case**: Deploy only on specific branches

```groovy
@Library('pipeline-library') _

pipeline {
    agent any
    
    stages {
        stage('Determine Environment') {
            steps {
                script {
                    def environment = 'dev'
                    def branch = env.GIT_BRANCH ?: 'develop'
                    
                    if (branch == 'main') {
                        environment = 'prod'
                    } else if (branch == 'staging') {
                        environment = 'staging'
                    }
                    
                    echo "Deploying to: ${environment}"
                    
                    buildAndDeploy(
                        environment: environment,
                        appName: 'auto-env-app',
                        repo: 'https://github.com/yourcompany/auto-env-app.git',
                        branch: branch,
                        deployment: "auto-env-app-${environment}"
                    )
                }
            }
        }
    }
}
```

### 10. Blue-Green Deployment

**Use Case**: Deploy to secondary deployment then switch

```groovy
@Library('pipeline-library') _

pipeline {
    agent any
    
    stages {
        stage('Deploy to Green') {
            steps {
                script {
                    buildAndDeploy(
                        environment: 'prod',
                        appName: 'blue-green-app',
                        repo: 'https://github.com/yourcompany/blue-green-app.git',
                        deployment: 'app-green',
                        skipDeploy: false
                    )
                }
            }
        }
        
        stage('Health Check Green') {
            steps {
                script {
                    healthCheck(
                        healthCheckUrl: 'https://green.example.com/health',
                        healthCheckRetries: 10
                    )
                }
            }
        }
        
        stage('Switch Traffic') {
            steps {
                input message: 'Switch traffic to green?'
                script {
                    sh '''
                        kubectl patch service app-service -n production \
                            -p '{"spec":{"selector":{"version":"green"}}}'
                    '''
                }
            }
        }
    }
}
```

## 🔧 Customization Tips

### Override Version Format

```groovy
buildAndDeploy(
    // ... other config ...
    version: "v2.${env.BUILD_NUMBER}.${env.GIT_COMMIT.take(7)}"
)
```

### Use Different Kubeconfig

```groovy
buildAndDeploy(
    // ... other config ...
    kubeconfigPath: '/custom/path/to/kubeconfig'
)
```

### Multiple Notifications

```groovy
buildAndDeploy(
    // ... other config ...
    notificationWebhook: 'https://slack-webhook...',
    postDeployScript: '''
        curl -X POST https://discord-webhook...
        curl -X POST https://teams-webhook...
    '''
)
```

## 📚 More Examples

For more examples and advanced use cases, check the main README.md file.
