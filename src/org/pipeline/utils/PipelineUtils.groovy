package org.pipeline.utils

/**
 * Utility functions for pipeline operations
 */
class PipelineUtils implements Serializable {
    
    /**
     * Generate version string
     * 
     * @param buildNumber Jenkins build number
     * @param prefix Version prefix (default: 'v1.0')
     * @return Version string (e.g., 'v1.0.123')
     */
    static String generateVersion(String buildNumber, String prefix = 'v1.0') {
        return "${prefix}.${buildNumber}"
    }
    
    /**
     * Generate version with Git commit hash
     * 
     * @param buildNumber Jenkins build number
     * @param gitCommit Git commit hash
     * @return Version string (e.g., 'v1.0.123-abc1234')
     */
    static String generateVersionWithCommit(String buildNumber, String gitCommit) {
        def shortCommit = gitCommit?.take(7) ?: 'unknown'
        return "v1.0.${buildNumber}-${shortCommit}"
    }
    
    /**
     * Generate timestamp-based version
     * 
     * @param buildNumber Jenkins build number
     * @return Version string with timestamp
     */
    static String generateTimestampVersion(String buildNumber) {
        def timestamp = new Date().format('yyyyMMdd-HHmmss')
        return "v1.0.${buildNumber}-${timestamp}"
    }
    
    /**
     * Parse application name from job name
     * 
     * @param jobName Jenkins job name
     * @return Application name
     */
    static String parseAppNameFromJob(String jobName) {
        // Remove Job prefix/suffix and environment names
        def name = jobName
            .replaceAll(/^Job-/, '')
            .replaceAll(/-(Dev|Staging|Prod|Production)$/, '')
            .toLowerCase()
        return name
    }
    
    /**
     * Parse environment from job name
     * 
     * @param jobName Jenkins job name
     * @return Environment name (dev, staging, prod)
     */
    static String parseEnvironmentFromJob(String jobName) {
        if (jobName =~ /(?i)prod(uction)?/) {
            return 'prod'
        } else if (jobName =~ /(?i)staging/) {
            return 'staging'
        } else {
            return 'dev'
        }
    }
    
    /**
     * Validate required fields in configuration
     * 
     * @param config Configuration map
     * @param requiredFields List of required field names
     * @throws IllegalArgumentException if any required field is missing
     */
    static void validateRequiredFields(Map config, List<String> requiredFields) {
        def missingFields = []
        requiredFields.each { field ->
            if (!config.containsKey(field) || config[field] == null) {
                missingFields.add(field)
            }
        }
        
        if (missingFields) {
            throw new IllegalArgumentException(
                "Missing required configuration fields: ${missingFields.join(', ')}"
            )
        }
    }
}
