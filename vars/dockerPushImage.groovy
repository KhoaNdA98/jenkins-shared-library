#!/usr/bin/env groovy
/**
 * Push Docker image to registry
 * 
 * @param config Pipeline configuration
 * @param pipelineConfig Computed pipeline configuration
 * @param envConfig Environment configuration
 */
def call(Map config, Map pipelineConfig, def envConfig) {
    echo "Pushing image to registry: ${pipelineConfig.registry}"
    
    def registryHandler = new org.pipeline.registries.RegistryHandler()
    
    try {
        // Login if required
        if (envConfig.requiresRegistryLogin) {
            def credentialsId = config.dockerCredentialsId ?: envConfig.dockerCredentialsId
            if (!credentialsId) {
                error "Docker registry requires login but no dockerCredentialsId provided. Specify dockerCredentialsId in your pipeline config."
            }
            
            withCredentials([usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'DOCKER_USER',
                passwordVariable: 'DOCKER_PASS'
            )]) {
                def registryUrl = pipelineConfig.registry.split('/')[0]
                sh """
                    echo \$DOCKER_PASS | docker login ${registryUrl} -u \$DOCKER_USER --password-stdin
                """
                echo "✅ Logged in to registry: ${registryUrl}"
            }
        }
        
        // Push image
        sh "docker push ${pipelineConfig.fullImageName}"
        echo "✅ Image pushed successfully: ${pipelineConfig.fullImageName}"
        
        // Tag as latest if configured
        if (config.tagLatest == true) {
            def latestTag = "${pipelineConfig.registry}/${pipelineConfig.imageName}:latest"
            sh """
                docker tag ${pipelineConfig.fullImageName} ${latestTag}
                docker push ${latestTag}
            """
            echo "✅ Latest tag pushed: ${latestTag}"
        }
        
    } catch (Exception e) {
        error "Failed to push Docker image: ${e.message}"
    }
}
