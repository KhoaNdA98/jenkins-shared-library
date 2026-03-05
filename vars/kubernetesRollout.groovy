#!/usr/bin/env groovy
/**
 * Deploy to Kubernetes using rollout restart
 * 
 * @param config Pipeline configuration
 * @param pipelineConfig Computed pipeline configuration
 * @param envConfig Environment configuration
 */
def call(Map config, Map pipelineConfig, def envConfig) {
    def namespace = config.namespace ?: config.environment
    def deployment = config.deployment ?: config.appName
    def kubeContext = config.kubeContext ?: envConfig.kubeContext
    
    echo "Kubernetes Deployment:"
    echo "  Context: ${kubeContext}"
    echo "  Namespace: ${namespace}"
    echo "  Deployment: ${deployment}"
    
    try {
        def kubeconfigPath = config.kubeconfigPath ?: envConfig.kubeconfigPath ?: '/var/jenkins_home/.kube/config'
        
        def kubectlCmd = "kubectl --kubeconfig=${kubeconfigPath}"
        if (kubeContext) {
            kubectlCmd += " --context=${kubeContext}"
        }
        
        // Check if deployment exists
        def checkCmd = "${kubectlCmd} get deployment ${deployment} -n ${namespace} 2>&1"
        def deploymentExists = sh(script: checkCmd, returnStatus: true) == 0
        
        if (!deploymentExists) {
            echo "⚠️  Deployment '${deployment}' not found in namespace '${namespace}'"
            
            if (config.createIfNotExists == true && config.manifestPath) {
                echo "Creating deployment from manifest..."
                sh "${kubectlCmd} apply -f ${config.manifestPath} -n ${namespace}"
            } else {
                error "Deployment does not exist. Set 'createIfNotExists: true' and provide 'manifestPath' to create it."
            }
        }
        
        // Update image if setImage is enabled
        if (config.setImage != false) {
            def containerName = config.containerName ?: config.appName
            echo "Updating container image: ${containerName} -> ${pipelineConfig.fullImageName}"
            sh """
                ${kubectlCmd} set image deployment/${deployment} \
                    ${containerName}=${pipelineConfig.fullImageName} \
                    -n ${namespace}
            """
        }
        
        // Rollout restart
        echo "Restarting deployment..."
        sh "${kubectlCmd} rollout restart deployment/${deployment} -n ${namespace}"
        
        // Wait for rollout to complete if configured
        if (config.waitForRollout != false) {
            def timeout = config.rolloutTimeout ?: '5m'
            echo "Waiting for rollout to complete (timeout: ${timeout})..."
            sh "${kubectlCmd} rollout status deployment/${deployment} -n ${namespace} --timeout=${timeout}"
        }
        
        // Show deployment status
        sh """
            echo "==================================="
            echo "Deployment Status:"
            ${kubectlCmd} get deployment ${deployment} -n ${namespace}
            echo ""
            echo "Pods:"
            ${kubectlCmd} get pods -n ${namespace} -l app=${config.appName} --sort-by=.metadata.creationTimestamp
            echo "==================================="
        """
        
        echo "✅ Kubernetes deployment completed"
        
    } catch (Exception e) {
        error "Kubernetes deployment failed: ${e.message}"
    }
}
