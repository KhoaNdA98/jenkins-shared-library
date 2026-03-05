#!/usr/bin/env groovy
/**
 * Deploy application based on deployment type
 * 
 * @param config Pipeline configuration
 * @param pipelineConfig Computed pipeline configuration
 * @param envConfig Environment configuration
 */
def call(Map config, Map pipelineConfig, def envConfig) {
    def deploymentType = config.deploymentType ?: 'kubernetes'
    
    echo "Deploying ${config.appName} to ${config.environment}"
    echo "Deployment type: ${deploymentType}"
    
    // Run pre-deploy script if provided
    if (config.preDeployScript) {
        echo "Running pre-deployment script..."
        sh config.preDeployScript
    }
    
    try {
        switch(deploymentType) {
            case 'kubernetes':
                kubernetesRollout(config, pipelineConfig, envConfig)
                break
                
            case 'kubernetes-manifest':
                kubernetesApplyManifest(config, pipelineConfig, envConfig)
                break
                
            case 'docker':
                dockerDeploy(config, pipelineConfig)
                break
                
            case 'docker-compose':
                dockerComposeDeploy(config, pipelineConfig)
                break
                
            default:
                error "Unsupported deployment type: ${deploymentType}"
        }
        
        echo "✅ Deployment completed successfully"
        
        // Run post-deploy script if provided
        if (config.postDeployScript) {
            echo "Running post-deployment script..."
            sh config.postDeployScript
        }
        
    } catch (Exception e) {
        error "Deployment failed: ${e.message}"
    }
}
