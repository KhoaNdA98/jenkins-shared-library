# Migration Guide: Level 2 Updates

## What Changed?

The library has been updated to be **truly generic** - all hardcoded infrastructure values have been removed. This means:

- ✅ No ClickAI-specific values
- ✅ No assumed registry URLs
- ✅ No assumed file paths
- ✅ No assumed credential IDs
- ✅ Works with any Git provider (GitHub, GitLab, Bitbucket, etc.)

## Required Actions

If you're using an older version of the library, you need to update your Jenkinsfiles to explicitly specify required parameters.

### Before (Old - Will Fail)

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    environment: 'dev',
    appName: 'my-app',
    repo: 'https://github.com/org/repo.git',
    branch: 'main'
)
```

### After (New - Required)

```groovy
@Library('pipeline-library') _

buildAndDeploy(
    // === REQUIRED PARAMETERS ===
    environment: 'dev',
    appName: 'my-app',
    repo: 'https://github.com/org/repo.git',
    branch: 'main',
    
    // NEW: Must specify registry
    registry: 'localhost:5000',
    
    // NEW: Must specify kubeconfig path
    kubeconfigPath: '/var/jenkins_home/.kube/config',
    
    // NEW: Must specify credentials explicitly
    credentialsId: 'github-pat',
    
    // For staging/prod with private registry:
    // dockerCredentialsId: 'docker-registry-credentials'
)
```

## Migration Checklist

### For DEV Environment

- [ ] Add `registry: 'localhost:5000'` (or your dev registry)
- [ ] Add `kubeconfigPath: '/var/jenkins_home/.kube/config'` (or your path)
- [ ] Add `credentialsId: 'github-pat'` (or your Git credential ID)

### For STAGING/PROD Environment

- [ ] Add `registry: 'your-registry.example.com'` (your production registry)
- [ ] Add `kubeconfigPath: '/var/jenkins_home/.kube/config'` (or your path)
- [ ] Add `credentialsId: 'github-pat'` (or your Git credential ID)
- [ ] Add `dockerCredentialsId: 'docker-registry-credentials'` (your Docker credential ID)

## Common Registry Formats

```groovy
// Local Docker registry
registry: 'localhost:5000'

// Docker Hub
registry: 'your-dockerhub-username'

// AWS ECR
registry: '123456789.dkr.ecr.us-east-1.amazonaws.com'

// Google Container Registry
registry: 'gcr.io/your-project-id'

// Azure Container Registry
registry: 'yourregistry.azurecr.io'

// Custom registry with path
registry: 'registry.example.com/project'
```

## Error Messages You Might See

### "Missing required configuration: 'registry'"

**Solution**: Add `registry: 'your-registry-url'` to your pipeline config.

### "Missing required configuration: 'kubeconfigPath'"

**Solution**: Add `kubeconfigPath: '/path/to/your/kubeconfig'` to your pipeline config.

### "Docker registry requires login but no dockerCredentialsId provided"

**Solution**: For staging/prod, add `dockerCredentialsId: 'your-docker-credentials'` to your pipeline config.

## Quick Fix Script

Use this script to check all your Jenkinsfiles:

```bash
# Find all Jenkinsfiles that might need updating
find . -name "Jenkinsfile*" -type f -exec grep -L "registry:" {} \;

# These files need to be updated with required parameters
```

## Need Help?

- Check [examples/](./examples/) for complete working examples
- Read [README.md](./README.md) for full documentation
- See [EXAMPLES.md](./EXAMPLES.md) for more use cases

## Benefits of These Changes

✅ **Universal**: Works for any project, any company
✅ **Transparent**: All configuration is visible in your Jenkinsfile
✅ **Flexible**: Easy to use different registries for different environments
✅ **Secure**: No hardcoded credentials or paths
✅ **Maintainable**: Library code doesn't need changes for different infrastructures
