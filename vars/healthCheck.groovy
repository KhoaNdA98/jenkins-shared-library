#!/usr/bin/env groovy
/**
 * Perform health check after deployment
 * 
 * @param config Pipeline configuration
 */
def call(Map config) {
    if (!config.healthCheckUrl) {
        return
    }
    
    def maxRetries = config.healthCheckRetries ?: 10
    def retryDelay = config.healthCheckDelay ?: 10
    def expectedStatus = config.healthCheckExpectedStatus ?: 200
    
    echo "Performing health check..."
    echo "URL: ${config.healthCheckUrl}"
    echo "Expected status: ${expectedStatus}"
    echo "Max retries: ${maxRetries}"
    
    for (int i = 1; i <= maxRetries; i++) {
        try {
            def response = sh(
                script: "curl -s -o /dev/null -w '%{http_code}' ${config.healthCheckUrl}",
                returnStdout: true
            ).trim()
            
            echo "Attempt ${i}/${maxRetries}: HTTP ${response}"
            
            if (response == expectedStatus.toString()) {
                echo "✅ Health check passed!"
                return
            }
            
        } catch (Exception e) {
            echo "Health check attempt ${i} failed: ${e.message}"
        }
        
        if (i < maxRetries) {
            echo "Waiting ${retryDelay}s before next attempt..."
            sleep retryDelay
        }
    }
    
    error "Health check failed after ${maxRetries} attempts"
}
