#!/usr/bin/env groovy
/**
 * Checkout code from Git repository
 * 
 * @param config Pipeline configuration map
 */
def call(Map config) {
    def branch = config.branch ?: 'main'
    def credentialsId = config.credentialsId ?: 'git-credentials'
    
    echo "Checking out ${config.repo} (branch: ${branch})"
    
    try {
        checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${branch}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'CleanBeforeCheckout']],
            userRemoteConfigs: [[
                url: config.repo,
                credentialsId: credentialsId
            ]]
        ])
        
        echo "✅ Code checkout successful"
        
        // Display commit information
        sh '''
            echo "Commit: $(git rev-parse --short HEAD)"
            echo "Author: $(git log -1 --pretty=format:'%an <%ae>')"
            echo "Message: $(git log -1 --pretty=format:'%s')"
        '''
    } catch (Exception e) {
        error "Failed to checkout code: ${e.message}"
    }
}
