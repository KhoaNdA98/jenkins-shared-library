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
                registry: 'localhost:5000',
                namespace: 'dev',
                kubeContext: 'dev',
                kubeconfigPath: '/var/jenkins_home/.kube/config',
                requiresRegistryLogin: false,
                requiresConfirmation: false,
                cleanupImages: false,
                imageNameTemplate: '{{appName}}',  // Simple naming for dev
                defaultBranch: 'develop'
            ],
            
            'staging': [
                registry: 'registry.example.com',
                namespace: 'staging',
                kubeContext: 'staging',
                kubeconfigPath: '/var/jenkins_home/.kube/config',
                requiresRegistryLogin: true,
                dockerCredentialsId: 'docker-registry-credentials',
                requiresConfirmation: false,
                cleanupImages: true,
                imageNameTemplate: '{{appName}}-staging',
                defaultBranch: 'staging'
            ],
            
            'prod': [
                registry: 'registry.example.com',
                namespace: 'production',
                kubeContext: 'production',
                kubeconfigPath: '/var/jenkins_home/.kube/config',
                requiresRegistryLogin: true,
                dockerCredentialsId: 'docker-registry-credentials',
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
