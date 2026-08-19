#!/usr/bin/env groovy
/**
 * Main pipeline function for building and deploying applications
 * 
 * @param config Map containing pipeline configuration:
 *   - environment: 'dev' | 'staging' | 'prod' (required)
 *   - appName: Application name (required)
 *   - repo: Git repository URL (required)
 *   - branch: Git branch name (default: 'main')
 *   - dockerfile: Path to Dockerfile (default: './Dockerfile')
 *   - buildContext: Docker build context (default: '.')
 *   - deployment: Kubernetes deployment name (optional)
 *   - namespace: Kubernetes namespace (optional, defaults to environment)
 *   - registry: Docker registry URL (optional)
 *   - credentialsId: Jenkins credentials ID for Git (default: 'git-credentials')
 *   - dockerCredentialsId: Jenkins credentials ID for Docker (optional)
 *   - kubeContext: Kubernetes context name (optional)
 *   - envFile: Create .env file (boolean, default: false)
 *   - envContent: Content for .env file (string, optional)
 *   - preDeployScript: Script to run before deployment (string, optional)
 *   - postDeployScript: Script to run after deployment (string, optional)
 *   - skipDeploy: Skip deployment stage (boolean, default: false)
 *   - buildArgs: Docker build arguments (Map, optional)
 *   - skipTests: Skip test stage (boolean, default: true)
 */
def call(Map config) {
    // Load configuration
    def envConfig = new org.pipeline.utils.EnvironmentConfig().getConfig(config.environment)
    def pipelineConfig = buildPipelineConfig(config, envConfig)
    
    pipeline {
        agent any
        
        stages {
            stage('Validation') {
                steps {
                    script {
                        validateConfig(config)
                        
                        // Set environment variables dynamically
                        env.VERSION = pipelineConfig.version
                        env.IMAGE_NAME = pipelineConfig.imageName
                        env.FULL_IMAGE_NAME = pipelineConfig.fullImageName
                        env.APP_NAME = config.appName
                        env.ENVIRONMENT = config.environment
                        
                        echo "==================================="
                        echo "Pipeline Configuration:"
                        echo "App: ${config.appName}"
                        echo "Environment: ${config.environment}"
                        echo "Version: ${pipelineConfig.version}"
                        echo "Image: ${pipelineConfig.fullImageName}"
                        echo "==================================="
                    }
                }
            }
            
            stage('Confirmation') {
                when {
                    expression { return envConfig.requiresConfirmation && config.skipConfirmation != true }
                }
                steps {
                    script {
                        timeout(time: 10, unit: 'MINUTES') {
                            input message: "Deploy to ${config.environment.toUpperCase()}?",
                                  ok: "YES - Deploy to ${config.environment.toUpperCase()}"
                        }
                    }
                }
            }
            
            stage('Checkout') {
                steps {
                    script {
                        if (config.cleanWorkspace == true) {
                            cleanWs()
                        }
                        checkoutCode(config)
                    }
                }
            }
            
            stage('Prepare Environment') {
                when {
                    expression { return config.envFile == true }
                }
                steps {
                    script {
                        if (config.envContent) {
                            def envPath = config.envFilePath ?: './.env'
                            writeFile file: envPath, text: config.envContent
                            echo "Environment file created at: ${envPath}"
                        }
                    }
                }
            }
            
            stage('Build Image') {
                steps {
                    script {
                        dockerBuild(config, pipelineConfig)
                    }
                }
            }
            
            stage('Push Image') {
                steps {
                    script {
                        dockerPushImage(config, pipelineConfig, envConfig)
                    }
                }
            }
            
            stage('Deploy') {
                when {
                    expression { return config.skipDeploy != true }
                }
                steps {
                    script {
                        deployApplication(config, pipelineConfig, envConfig)
                    }
                }
            }
            
            stage('Post Deploy Verification') {
                when {
                    expression { return config.skipDeploy != true && config.healthCheckUrl }
                }
                steps {
                    script {
                        healthCheck(config)
                    }
                }
            }

            // Optional, opt-in stage — chỉ chạy khi job truyền config.buildAgentBinaries
            // = true. Mọi job KHÔNG set flag này (mặc định) đi qua stage này y hệt bản
            // cũ, hoàn toàn không đổi hành vi — thêm an toàn cho các job khác đang dùng
            // chung thư viện. Đóng gói mcp-server/ (Node.js) thành 4 binary độc lập
            // (win/mac-x64/mac-arm64/linux) không liên quan gì Docker/K8s ở trên, nên
            // build trong 1 container node:22 tạm thời (agent Jenkins không có sẵn
            // Node.js) — lỗi ở stage này KHÔNG được làm fail cả pipeline (đã deploy
            // gateway xong ở stage trước là quan trọng nhất, build binary là phụ).
            //
            // --volumes-from "$HOSTNAME" (KHÔNG dùng -v "$WORKSPACE":/workspace) —
            // đã tự verify lỗi thật trên build live: Jenkins agent ở đây chạy bằng
            // Docker-outside-of-Docker (chỉ mount docker.sock, không phải Docker
            // lồng Docker), nên `docker run` ở đây thực chất nhờ DAEMON CỦA HOST tạo
            // container SIBLING — path kiểu "$WORKSPACE" (VD
            // /var/jenkins_home/workspace/...) chỉ tồn tại BÊN TRONG container
            // Jenkins, KHÔNG map được sang path thật trên host (-v báo lỗi
            // "read-only file system" vì host không hề có thư mục đó). Container
            // Jenkins tự có hostname = chính container ID của nó (Docker mặc định) —
            // --volumes-from "$HOSTNAME" mượn lại NGUYÊN mount hiện có của chính nó
            // (đã tự verify bằng tay: `docker run --volumes-from <jenkins-id> node:22
            // node --version` chạy được, thấy đúng file) — né hẳn việc phải biết path
            // thật trên host.
            stage('Build Agent Binaries') {
                when {
                    expression { return config.buildAgentBinaries == true }
                }
                steps {
                    script {
                        try {
                            // Backend (Node, đóng gói qua pkg — không đổi), rồi tới
                            // neutralino-shell/ — launcher native (cửa sổ + tray) mỏng, tự
                            // spawn CHÍNH backend vừa build ở trên làm tiến trình con lúc
                            // chạy thật — xem neutralino-shell/README.md. `neu build` KHÔNG
                            // compile gì (tự tải binary framework build sẵn), nên build được
                            // luôn trong image node:22-slim, không cần thêm lib GTK/WebKit gì
                            // ở bước ĐÓNG GÓI này (chỉ cần lúc CHẠY thật trên máy có desktop).
                            sh '''
                                docker run --rm \
                                    --volumes-from "$HOSTNAME" \
                                    -w "$WORKSPACE" \
                                    node:22-slim \
                                    sh -c "
                                        npm ci &&
                                        npm run build:binaries &&
                                        cd neutralino-shell &&
                                        npm ci &&
                                        npx neu update &&
                                        npx neu build --release
                                    "
                            '''
                            // `neu build --release` LUÔN build đủ 7 kiến trúc nó hỗ trợ (không
                            // có flag chọn platform) + 1 file zip tự gói lại y hệt — chỉ 4 cái
                            // (linux_x64/win_x64/mac_x64/mac_arm64) khớp 4 target backend
                            // (pkg.targets trong package.json) là thật sự cần; linux_arm64,
                            // linux_armhf, mac_universal, và file .zip tổng là rác không dùng
                            // tới (đã tự build+ls thật để xác nhận danh sách, không đoán suông)
                            // — lọc bớt cho archive khỏi phình.
                            archiveArtifacts artifacts: 'dist/clickai-mcp-*', fingerprint: true, allowEmptyArchive: true
                            archiveArtifacts artifacts: 'neutralino-shell/dist/**/*-linux_x64,neutralino-shell/dist/**/*-win_x64.exe,neutralino-shell/dist/**/*-mac_x64,neutralino-shell/dist/**/*-mac_arm64,neutralino-shell/dist/**/resources.neu', fingerprint: true, allowEmptyArchive: true
                            echo "✅ Built & archived standalone agent binaries + Neutralino shell launchers."
                        } catch (Exception e) {
                            echo "⚠️ Agent binaries build failed (không ảnh hưởng deploy gateway đã xong ở stage trước): ${e.message}"
                            currentBuild.result = 'UNSTABLE'
                        }
                    }
                }
            }
        }

        post {
            success {
                script {
                    echo "✅ ${config.appName} build and deployment completed successfully!"
                    echo "Version: ${pipelineConfig.version}"
                    echo "Environment: ${config.environment}"
                    
                    if (config.notificationWebhook) {
                        sendNotification(config, 'success', pipelineConfig)
                    }
                }
            }
            failure {
                script {
                    echo "❌ ${config.appName} build or deployment failed!"
                    
                    if (config.notificationWebhook) {
                        sendNotification(config, 'failure', pipelineConfig)
                    }
                }
            }
            always {
                script {
                    cleanup(config, pipelineConfig, envConfig)
                }
            }
        }
    }
}

def buildPipelineConfig(Map config, def envConfig) {
    def version = config.version ?: "v1.0.${env.BUILD_NUMBER}"
    def imageNameBase = config.imageName ?: config.appName
    
    // Build image name based on environment config
    def imageName = envConfig.imageNameTemplate ? 
        envConfig.imageNameTemplate.replace('{{appName}}', imageNameBase).replace('{{environment}}', config.environment) :
        "${imageNameBase}-${config.environment}"
    
    def registry = config.registry ?: envConfig.registry
    def fullImageName = "${registry}/${imageName}:${version}"
    
    return [
        version: version,
        imageName: imageName,
        fullImageName: fullImageName,
        registry: registry
    ]
}

def validateConfig(Map config) {
    // Validate required fields
    def required = ['environment', 'appName', 'repo']
    required.each { field ->
        if (!config[field]) {
            error "Missing required configuration: ${field}"
        }
    }
    
    // Validate environment
    def validEnvironments = ['dev', 'staging', 'prod']
    if (!(config.environment in validEnvironments)) {
        error "Invalid environment: ${config.environment}. Must be one of: ${validEnvironments.join(', ')}"
    }
    
    // Validate registry is provided (either in config or as parameter)
    def envConfig = new org.pipeline.utils.EnvironmentConfig().getConfig(config.environment)
    def registry = config.registry ?: envConfig.registry
    if (!registry) {
        error """
Missing required configuration: 'registry'

You must specify a Docker registry in your pipeline configuration:
  registry: 'your-registry.example.com'  // Or 'localhost:5000' for local dev

Examples:
  - Local registry: 'localhost:5000'
  - Docker Hub: 'your-dockerhub-username'
  - AWS ECR: '123456789.dkr.ecr.us-east-1.amazonaws.com'
  - GCR: 'gcr.io/your-project-id'
  - Custom: 'registry.example.com/project'
        """
    }
    
    // Validate kubeconfigPath for deployment
    if (config.skipDeploy != true) {
        def kubeconfigPath = config.kubeconfigPath ?: envConfig.kubeconfigPath
        if (!kubeconfigPath) {
            error """
Missing required configuration: 'kubeconfigPath'

You must specify the path to your Kubernetes config file:
  kubeconfigPath: '/path/to/your/kubeconfig'

Common examples:
  - Jenkins container: '/var/jenkins_home/.kube/config'
  - Local: '\$HOME/.kube/config' or '/root/.kube/config'
  - Custom path: '/path/to/custom-kubeconfig.yaml'

Tip: Mount your kubeconfig file into Jenkins container.
            """
        }
    }
}
