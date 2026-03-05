package org.pipeline.registries

/**
 * Handle Docker registry operations (login, logout)
 */
class RegistryHandler implements Serializable {
    
    private def steps
    
    RegistryHandler(steps) {
        this.steps = steps
    }
    
    /**
     * Login to Docker registry
     * 
     * @param registryUrl Registry URL (e.g., registry.example.com)
     * @param credentialsId Jenkins credentials ID
     */
    void login(String registryUrl, String credentialsId) {
        steps.echo "Logging in to registry: ${registryUrl}"
        
        steps.withCredentials([
            steps.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'DOCKER_USER',
                passwordVariable: 'DOCKER_PASS'
            )
        ]) {
            steps.sh """
                echo \$DOCKER_PASS | docker login ${registryUrl} -u \$DOCKER_USER --password-stdin
            """
        }
    }
    
    /**
     * Logout from Docker registry
     * 
     * @param registryUrl Registry URL
     */
    void logout(String registryUrl) {
        try {
            steps.sh "docker logout ${registryUrl} || true"
            steps.echo "Logged out from registry: ${registryUrl}"
        } catch (Exception e) {
            steps.echo "Warning: Failed to logout from registry: ${e.message}"
        }
    }
    
    /**
     * Parse registry URL from full image name
     * 
     * @param fullImageName Full Docker image name (registry/repo:tag)
     * @return Registry URL
     */
    static String parseRegistryUrl(String fullImageName) {
        def parts = fullImageName.split('/')
        if (parts.length < 2) {
            return 'docker.io'  // Default Docker Hub
        }
        return parts[0]
    }
}
