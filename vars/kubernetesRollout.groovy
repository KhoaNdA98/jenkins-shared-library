#!/usr/bin/env groovy
/**
 * Deploy to Kubernetes using rollout restart.
 *
 * Optimizations vs. original:
 *   - Default rolloutTimeout reduced 5m → 2m (matches typical readinessProbe).
 *     Override via config.rolloutTimeout if your app needs longer.
 *   - Prints recent pod Events on rollout failure so you don't have to
 *     manually kubectl describe after a failed deploy.
 *   - rolloutPollInterval (config option, informational) documents the
 *     recommended readinessProbe tuning in K8s manifests.
 *
 * New config options:
 *   - rolloutTimeout     : kubectl --timeout value (default: '2m')
 *   - rolloutPollInterval: reminder comment – tune readinessProbe.periodSeconds
 *                          in your K8s manifest to ≤5 for faster rollout
 */
def call(Map config, Map pipelineConfig, def envConfig) {
    def namespace   = config.namespace   ?: config.environment
    def deployment  = config.deployment  ?: config.appName
    def kubeContext = config.kubeContext ?: envConfig.kubeContext
    
    echo "Kubernetes Deployment:"
    echo "  Context: ${kubeContext}"
    echo "  Namespace: ${namespace}"
    echo "  Deployment: ${deployment}"
    
    try {
        def kubeconfigPath = config.kubeconfigPath ?: envConfig.kubeconfigPath
        
        if (!kubeconfigPath) {
            error "kubeconfigPath is required. Specify in pipeline config or environment config."
        }
        
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
        
        // Wait for rollout to complete (2 min default – tune readinessProbe.periodSeconds ≤5 in K8s manifest)
        if (config.waitForRollout != false) {
            def timeout = config.rolloutTimeout ?: '2m'
            echo "Waiting for rollout to complete (timeout: ${timeout})..."
            echo "💡 Tip: set readinessProbe.periodSeconds ≤5 in your K8s manifest to hit this timeout comfortably."
            try {
                sh "${kubectlCmd} rollout status deployment/${deployment} -n ${namespace} --timeout=${timeout}"
            } catch (Exception rolloutEx) {
                // Print pod events to help diagnose the failure without manual kubectl
                echo "⚠️  Rollout did not finish in time – dumping recent pod events:"
                sh """
                    ${kubectlCmd} describe pods -n ${namespace} -l app=${config.appName} \
                        | grep -A 20 'Events:' || true
                """
                error "Rollout failed: ${rolloutEx.message}"
            }
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
