#!/usr/bin/env groovy
/**
 * Checkout code from Git repository
 * Supports: GitHub PAT token (current), SSH keys (recommended), or public repos
 * 
 * @param config Pipeline configuration map
 *   - repo: Git repository URL (required)
 *   - branch: Branch name (default: 'main')
 *   - credentialsId: Jenkins credentials ID (optional for public repos)
 *   - useSSH: Use SSH instead of HTTPS (default: false, recommended: true for better performance)
 */
def call(Map config) {
    def branch = config.branch ?: 'main'
    def credentialsId = config.credentialsId  // No default - must be specified or null for public repos
    
    echo "Checking out ${config.repo} (branch: ${branch})"
    
    try {
        // Method 1: SSH Key (RECOMMENDED - fastest & most secure)
        if (config.useSSH == true) {
            def sshRepo = convertToSSH(config.repo)
            echo "Using SSH authentication (recommended method)"
            
            checkout([
                $class: 'GitSCM',
                branches: [[name: "*/${branch}"]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [[$class: 'CleanBeforeCheckout']],
                userRemoteConfigs: [[
                    url: sshRepo,
                    credentialsId: credentialsId  // Should be SSH key credential
                ]]
            ])
        }
        // Method 2: HTTPS with PAT token (current method)
        else if (credentialsId) {
            echo "Using HTTPS with Personal Access Token"
            
            // Support both Secret text and Username/Password credential types
            withCredentials([string(
                credentialsId: credentialsId,
                variable: 'GIT_TOKEN'
            )]) {
                // Use x-access-token format (GitHub standard)
                def repoUrl = config.repo
                    .replaceAll('https://', '')
                    .replaceAll('http://', '')
                    .replaceAll('.git$', '')
                
                def authenticatedUrl = "https://x-access-token:${GIT_TOKEN}@${repoUrl}.git"
                
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${branch}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [[$class: 'CleanBeforeCheckout']],
                    userRemoteConfigs: [[url: authenticatedUrl]]
                ])
            }
        }
        // Method 3: Public repo without credentials
        else {
            echo "Using public repository access (no credentials)"
            
            checkout([
                $class: 'GitSCM',
                branches: [[name: "*/${branch}"]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [[$class: 'CleanBeforeCheckout']],
                userRemoteConfigs: [[url: config.repo]]
            ])
        }
        
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

/**
 * Convert HTTPS URL to SSH format (supports GitHub, GitLab, Bitbucket, and custom Git servers)
 */
def convertToSSH(String httpsUrl) {
    // Extract domain and path from HTTPS URL
    def cleaned = httpsUrl
        .replaceAll('^https?://', '')  // Remove http:// or https://
        .replaceAll('.git$', '')        // Remove .git suffix if present
    
    // Split into domain and path
    def parts = cleaned.split('/', 2)
    if (parts.size() < 2) {
        error "Invalid Git URL format: ${httpsUrl}"
    }
    
    def domain = parts[0]
    def path = parts[1]
    
    // Convert to SSH format: git@domain:path.git
    return "git@${domain}:${path}.git"
}
