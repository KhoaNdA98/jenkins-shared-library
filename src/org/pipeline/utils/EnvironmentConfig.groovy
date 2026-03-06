package org.pipeline.utils

/**
 * Environment configuration for different deployment environments
 */
class EnvironmentConfig implements Serializable {
    
    /**
     * Get configuration for specified environment
     * 
     * @param environment Environment name (dev, staging, prod)
     * @return Map containing environment-specific configuration
     */
    Map getConfig(String environment) {
        def configs = [
            'dev': [
                registry: null,  // REQUIRED: Specify in pipeline (e.g., 'localhost:5000' or 'your-registry.com')
                namespace: 'dev',
                kubeContext: 'dev',
                kubeconfigPath: null,  // REQUIRED: Specify in pipeline (e.g., '/var/jenkins_home/.kube/config')
                requiresRegistryLogin: false,
                dockerCredentialsId: null,  // Optional: Set if registry requires login
                requiresConfirmation: false,
                cleanupImages: false,
                imageNameTemplate: '{{appName}}',  // Simple naming for dev
                defaultBranch: 'develop'
            ],
            
            'staging': [
                registry: null,  // REQUIRED: Specify in pipeline (e.g., 'registry.example.com/project')
                namespace: 'staging',
                kubeContext: 'staging',
                kubeconfigPath: null,  // REQUIRED: Specify in pipeline
                requiresRegistryLogin: true,
                dockerCredentialsId: null,  // REQUIRED if requiresRegistryLogin: true
                requiresConfirmation: false,
                cleanupImages: true,
                imageNameTemplate: '{{appName}}-staging',
                defaultBranch: 'staging'
            ],
            
            'prod': [
                registry: null,  // REQUIRED: Specify in pipeline
                namespace: 'prod',
                kubeContext: 'prod',
                kubeconfigPath: null,  // REQUIRED: Specify in pipeline
                requiresRegistryLogin: true,
                dockerCredentialsId: null,  // REQUIRED if requiresRegistryLogin: true
                requiresConfirmation: true,
                cleanupImages: true,
                imageNameTemplate: '{{appName}}-prod',
                defaultBranch: 'main'
            ]
        ]
        
        if (!configs.containsKey(environment)) {
            throw new IllegalArgumentException("Unknown environment: ${environment}")
        }
        
        return configs[environment]
    }
    
    /**
     * Get list of available environments
     * 
     * @return List of environment names
     */
    static List<String> getAvailableEnvironments() {
        return ['dev', 'staging', 'prod']
    }
}
