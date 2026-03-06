#!/usr/bin/env groovy
/**
 * Build Docker image
 * 
 * @param config Pipeline configuration
 * @param pipelineConfig Computed pipeline configuration
 */
def call(Map config, Map pipelineConfig) {
    def dockerfile = config.dockerfile ?: './Dockerfile'
    def buildContext = config.buildContext ?: '.'
    def platform = config.platform ?: 'linux/amd64'
    
    echo "Building Docker image: ${pipelineConfig.fullImageName}"
    echo "Dockerfile: ${dockerfile}"
    echo "Build context: ${buildContext}"
    echo "Platform: ${platform}"
    
    def buildArgsString = ''
    if (config.buildArgs) {
        config.buildArgs.each { key, value ->
            buildArgsString += " --build-arg ${key}=${value}"
        }
    }
    
    try {
        sh """
            docker buildx build \
                --platform ${platform} \
                --load \
                -t ${pipelineConfig.fullImageName} \
                --build-arg BUILD_VERSION=${pipelineConfig.version} \
                --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} \
                --build-arg BUILD_DATE=\$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
                --build-arg GIT_COMMIT=\$(git rev-parse --short HEAD) \
                ${buildArgsString} \
                -f ${dockerfile} \
                ${buildContext}
        """
        
        echo "✅ Docker image built successfully"
        
        // Display image information
        sh "docker images ${pipelineConfig.fullImageName}"
        
    } catch (Exception e) {
        error "Failed to build Docker image: ${e.message}"
    }
}
