#!/usr/bin/env groovy
/**
 * Cleanup resources after pipeline execution
 * 
 * @param config Pipeline configuration
 * @param pipelineConfig Computed pipeline configuration
 * @param envConfig Environment configuration
 */
def call(Map config, Map pipelineConfig, def envConfig) {
    echo "Cleaning up resources..."
    
    try {
        // Logout from Docker registry if required
        if (envConfig.requiresRegistryLogin) {
            def registryUrl = pipelineConfig.registry.split('/')[0]
            sh "docker logout ${registryUrl} || true"
            echo "Logged out from registry"
        }
        
        // Remove local image if cleanup is enabled
        if (envConfig.cleanupImages || config.cleanupImages) {
            sh "docker rmi ${pipelineConfig.fullImageName} || true"
            echo "Removed local image"
            
            if (config.tagLatest == true) {
                def latestTag = "${pipelineConfig.registry}/${pipelineConfig.imageName}:latest"
                sh "docker rmi ${latestTag} || true"
            }
        }
        
        // Clean workspace if enabled (default)
        if (config.cleanWorkspace != false) {
            cleanWs()
            echo "Workspace cleaned"
        }
        
        echo "✅ Cleanup completed"
        
    } catch (Exception e) {
        echo "⚠️  Cleanup warning: ${e.message}"
        // Don't fail pipeline on cleanup errors
    }
}
