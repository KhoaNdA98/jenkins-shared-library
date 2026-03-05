#!/usr/bin/env groovy
/**
 * Send notification to webhook (Slack, Discord, Teams, etc.)
 * 
 * @param config Pipeline configuration
 * @param status 'success' or 'failure'
 * @param pipelineConfig Computed pipeline configuration
 */
def call(Map config, String status, Map pipelineConfig) {
    if (!config.notificationWebhook) {
        return
    }
    
    def color = status == 'success' ? 'good' : 'danger'
    def emoji = status == 'success' ? '✅' : '❌'
    def statusText = status.toUpperCase()
    
    def message = """
    {
        "attachments": [{
            "color": "${color}",
            "title": "${emoji} Pipeline ${statusText}",
            "fields": [
                {
                    "title": "Application",
                    "value": "${config.appName}",
                    "short": true
                },
                {
                    "title": "Environment",
                    "value": "${config.environment}",
                    "short": true
                },
                {
                    "title": "Version",
                    "value": "${pipelineConfig.version}",
                    "short": true
                },
                {
                    "title": "Build",
                    "value": "#${env.BUILD_NUMBER}",
                    "short": true
                },
                {
                    "title": "Branch",
                    "value": "${config.branch ?: 'main'}",
                    "short": true
                },
                {
                    "title": "Duration",
                    "value": "${currentBuild.durationString}",
                    "short": true
                }
            ],
            "footer": "Jenkins CI/CD",
            "ts": ${currentBuild.startTimeInMillis / 1000}
        }]
    }
    """
    
    try {
        sh """
            curl -X POST -H 'Content-type: application/json' \
                --data '${message}' \
                ${config.notificationWebhook}
        """
        echo "Notification sent"
    } catch (Exception e) {
        echo "⚠️  Failed to send notification: ${e.message}"
    }
}
