package org.pipeline.deployers

/**
 * Kubernetes deployment operations
 */
class KubernetesDeployer implements Serializable {
    
    private def steps
    private String kubeconfigPath
    private String context
    
    KubernetesDeployer(steps, String kubeconfigPath, String context = null) {
        this.steps = steps
        this.kubeconfigPath = kubeconfigPath
        this.context = context
    }
    
    /**
     * Build kubectl command with context and kubeconfig
     * 
     * @return Base kubectl command string
     */
    private String buildKubectlCmd() {
        def cmd = "kubectl --kubeconfig=${kubeconfigPath}"
        if (context) {
            cmd += " --context=${context}"
        }
        return cmd
    }
    
    /**
     * Check if deployment exists
     * 
     * @param deploymentName Deployment name
     * @param namespace Namespace
     * @return Boolean indicating if deployment exists
     */
    boolean deploymentExists(String deploymentName, String namespace) {
        def cmd = "${buildKubectlCmd()} get deployment ${deploymentName} -n ${namespace}"
        def exitCode = steps.sh(script: "${cmd} 2>&1", returnStatus: true)
        return exitCode == 0
    }
    
    /**
     * Update deployment image
     * 
     * @param deploymentName Deployment name
     * @param containerName Container name
     * @param imageName Full image name with tag
     * @param namespace Namespace
     */
    void setImage(String deploymentName, String containerName, String imageName, String namespace) {
        steps.echo "Updating image for deployment/${deploymentName}, container: ${containerName}"
        steps.sh """
            ${buildKubectlCmd()} set image deployment/${deploymentName} \
                ${containerName}=${imageName} \
                -n ${namespace}
        """
    }
    
    /**
     * Rollout restart deployment
     * 
     * @param deploymentName Deployment name
     * @param namespace Namespace
     */
    void rolloutRestart(String deploymentName, String namespace) {
        steps.echo "Restarting deployment: ${deploymentName}"
        steps.sh "${buildKubectlCmd()} rollout restart deployment/${deploymentName} -n ${namespace}"
    }
    
    /**
     * Wait for rollout to complete
     * 
     * @param deploymentName Deployment name
     * @param namespace Namespace
     * @param timeout Timeout duration (e.g., '5m', '300s')
     */
    void waitForRollout(String deploymentName, String namespace, String timeout = '5m') {
        steps.echo "Waiting for rollout to complete (timeout: ${timeout})"
        steps.sh """
            ${buildKubectlCmd()} rollout status deployment/${deploymentName} \
                -n ${namespace} \
                --timeout=${timeout}
        """
    }
    
    /**
     * Get deployment status
     * 
     * @param deploymentName Deployment name
     * @param namespace Namespace
     */
    void getStatus(String deploymentName, String namespace) {
        steps.sh """
            echo "==================================="
            echo "Deployment Status:"
            ${buildKubectlCmd()} get deployment ${deploymentName} -n ${namespace}
            echo ""
            echo "Pods:"
            ${buildKubectlCmd()} get pods -n ${namespace} -l app=${deploymentName}
            echo "==================================="
        """
    }
}
