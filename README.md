# Jenkins Shared Pipeline Library

A generic, reusable Jenkins Shared Library for building and deploying applications to Kubernetes clusters.

## 🎯 Features

- ✅ Multi-environment support (dev, staging, prod)
- ✅ Kubernetes deployment (K3s, K8s, cloud providers)
- ✅ Docker registry integration (local, cloud, private registries)
- ✅ Automatic versioning with build numbers
- ✅ Git authentication (PAT, SSH, public repos)
- ✅ Health checks after deployment
- ✅ Extensible and customizable

## 📦 Installation

### 1. Setup in Jenkins

1. Go to **Manage Jenkins** → **Configure System**
2. Scroll to **Global Pipeline Libraries**
3. Click **Add**
4. Configure:
   - **Name**: `pipeline-library` (or your preferred name)
   - **Default version**: `main`
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: Your repository URL
   - **Credentials**: Your Git credentials (if private repo)

### 2. Configure Jenkins Credentials

Add required credentials in Jenkins (**Credentials** → **System** → **Global credentials**):

- **Git Credentials**: Secret text (PAT) or SSH key
- **Docker Registry Credentials**: Username with password (for private registries)

## 🚀 Usage

### Basic Example

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    // Required
    environment: 'dev',
    appName: 'my-app',
    repo: 'https://github.com/your-org/your-repo.git',
    branch: 'main',
    registry: 'your-registry-url',
    kubeconfigPath: '/path/to/kubeconfig',
    credentialsId: 'your-git-credentials',
    
    // Optional
    dockerfile: './Dockerfile',
    deployment: 'my-deployment',
    namespace: 'dev'
)
```

## 📋 Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `environment` | String | Deployment environment: `'dev'`, `'staging'`, or `'prod'` |
| `appName` | String | Application name for image naming |
| `repo` | String | Git repository URL |
| `registry` | String | Docker registry URL |
| `kubeconfigPath` | String | Path to kubeconfig file in Jenkins |
| `credentialsId` | String/null | Git credential ID (null for public repos) |

**Additional for staging/prod:**
- `dockerCredentialsId` (String): Docker registry credential ID

## 🔧 Optional Parameters

### Git Configuration
- `branch`: Branch name (default: `'main'`)
- `useSSH`: Use SSH instead of HTTPS (default: `false`)

### Docker Build
- `dockerfile`: Path to Dockerfile (default: `'./Dockerfile'`)
- `buildContext`: Build context path (default: `'.'`)
- `platform`: Target platform (default: `'linux/amd64'`)
- `buildArgs`: Map of build arguments

### Kubernetes Deployment
- `deployment`: Deployment name (default: appName)
- `namespace`: K8s namespace (default: environment)
- `kubeContext`: K8s context name
- `containerName`: Container name (default: appName)
- `setImage`: Update image before rollout (default: `true`)
- `waitForRollout`: Wait for rollout completion (default: `true`)
- `rolloutTimeout`: Timeout duration (default: `'5m'`)

### Environment File
- `envFile`: Create .env file (default: `false`)
- `envFilePath`: Path for .env file
- `envContent`: Content for .env file

### Other Options
- `version`: Override auto-generated version
- `skipDeploy`: Skip deployment stage (default: `false`)
- `skipHealthCheck`: Skip health check (default: `false`)
- `cleanupImages`: Remove local images after push (default: `false`)

## 📚 Environment Configuration

The library supports three pre-configured environments:

### Development
- Namespace: `dev`
- Requires confirmation: No
- Cleanup images: No

### Staging
- Namespace: `staging`
- Requires registry login: Yes
- Requires confirmation: No
- Cleanup images: Yes

### Production
- Namespace: `prod`
- Requires registry login: Yes
- **Requires manual confirmation**: Yes ⚠️
- Cleanup images: Yes

## 🏗️ Library Structure

```
jenkins-shared-library/
├── vars/                    # Pipeline functions
│   ├── buildAndDeploy.groovy
│   ├── checkoutCode.groovy
│   ├── dockerBuild.groovy
│   ├── dockerPushImage.groovy
│   ├── deployApplication.groovy
│   ├── kubernetesRollout.groovy
│   ├── healthCheck.groovy
│   ├── sendNotification.groovy
│   └── cleanup.groovy
├── src/org/pipeline/        # Helper classes
│   ├── utils/
│   │   └── EnvironmentConfig.groovy
│   ├── registries/
│   │   └── RegistryHandler.groovy
│   └── kubernetes/
│       └── KubernetesDeployer.groovy
└── resources/               # Optional resources
```

## 🔒 Security Notes

- Never commit sensitive information (credentials, tokens, IPs) to this repository
- Use Jenkins credentials for all sensitive data
- Keep your kubeconfig files secure
- Use SSH keys instead of PAT tokens when possible
- Enable manual confirmation for production deployments

## 📖 License

See [LICENSE](LICENSE) file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📞 Support

For issues or questions, please open an issue in the repository.
