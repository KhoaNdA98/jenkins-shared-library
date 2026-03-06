# So sánh Pipeline Cũ vs Mới (Shared Library)

## 📊 Tổng quan

| Aspect | Pipeline Cũ | Pipeline Mới (Shared Library) |
|--------|-------------|------------------------------|
| Số dòng code | ~120 dòng | ~30 dòng (giảm 75%) |
| Stages | 5 stages | 8 stages (tự động) |
| Error handling | Thủ công | Tự động với validation |
| Reusability | ❌ Không | ✅ Có |
| Maintenance | Khó (phải sửa từng file) | Dễ (sửa 1 lần ở library) |

## 🔄 Mapping: Cũ → Mới

### Environment Variables (Cũ)

```groovy
environment {
    KUBECONFIG = '/var/jenkins_home/.kube/config'
    DOCKER_REGISTRY = 'localhost:5000'
    VERSION = "v0.0.${env.BUILD_NUMBER}"
    GITHUB_CREDS = credentials('github-pat')
    NAMESPACE = 'dev'
    BRANCH = 'feat/login_wrapper'
    REPO_NAME = 'clickai-dify'
    APP_NAME = 'coreweb-v2'
    DEPLOYMENT_NAME = "clickai-${env.APP_NAME}"
    // ... nhiều variables khác
}
```

### Parameters (Mới)

```groovy
buildAndDeploy(
    environment: 'dev',
    appName: 'coreweb-v2',
    repo: 'https://github.com/clickaivn/clickai-dify.git',
    branch: 'feat/login_wrapper',
    registry: 'localhost:5000',
    kubeconfigPath: '/var/jenkins_home/.kube/config',
    credentialsId: 'github-pat',
    // ... các params khác
)
```

## 📝 Chi tiết từng phần

### 1. Checkout Stage

**Cũ:**
```groovy
stage('Checkout') {
    steps {
        cleanWs()
        echo "Starting build..."
        checkout([$class: 'GitSCM',
            branches: [[name: "*/${env.BRANCH}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'CleanBeforeCheckout']],
            userRemoteConfigs: [[url: "${env.REPO_URL}"]]
        ])
    }
}
```

**Mới:**
```groovy
// Tự động handle bởi shared library
repo: 'https://github.com/clickaivn/clickai-dify.git',
branch: 'feat/login_wrapper',
credentialsId: 'github-pat',
```

### 2. Prepare Environment (tạo .env file)

**Cũ:**
```groovy
stage('Prepare Environment') {
    steps {
        script {
            def envContent = """
            PORT=3001
            NEXT_PUBLIC_ENDPOINT_AUTH=/auth/api/
            ...
            """
            writeFile file: './web/.env', text: envContent
        }
    }
}
```

**Mới:**
```groovy
envFile: true,
envFilePath: './web/.env',
envContent: '''
PORT=3001
NEXT_PUBLIC_ENDPOINT_AUTH=/auth/api/
...
'''
```

### 3. Build Image

**Cũ:**
```groovy
stage('Build Image') {
    steps {
        sh """
            docker buildx build --platform ${env.BUILD_PLATFORM} \\
            --build-arg NEXT_PUBLIC_BASE_PATH=/studio \\
            -t ${env.FULL_IMAGE_NAME} \\
            -f ./web/Dockerfile ./web
        """
    }
}
```

**Mới:**
```groovy
dockerfile: './web/Dockerfile',
buildContext: './web',
platform: 'linux/amd64',
buildArgs: [
    NEXT_PUBLIC_BASE_PATH: '/studio'
]
```

### 4. Push Image

**Cũ:**
```groovy
stage('Push Image') {
    steps {
        sh """
            echo ${DOCKER_CREDENTIALS_PSW} | docker login ${env.DOCKER_REGISTRY} -u ${DOCKER_CREDENTIALS_USR} --password-stdin
            docker push ${env.FULL_IMAGE_NAME}
        """
    }
}
```

**Mới:**
```groovy
// Tự động handle (không cần login cho localhost:5000)
registry: 'localhost:5000'
```

### 5. Deploy

**Cũ:**
```groovy
stage('Deploy') {
    when {
        expression { return env.DEPLOY == 'true' }
    }
    steps {
        sh """
            kubectl config use-context ${env.KUBE_CONTEXT}
            kubectl set image deployment/${env.DEPLOYMENT_NAME} \\
                ${env.APP_NAME}=${env.FULL_IMAGE_NAME} \\
                -n ${env.NAMESPACE}
            kubectl rollout status deployment/${env.DEPLOYMENT_NAME} \\
                -n ${env.NAMESPACE} --timeout=120s
        """
    }
}
```

**Mới:**
```groovy
deployment: 'clickai-coreweb-v2',
namespace: 'dev',
kubeContext: 'dev',
containerName: 'coreweb-v2',
setImage: true,
waitForRollout: true,
rolloutTimeout: '2m'
```

### 6. Post Actions

**Cũ:**
```groovy
post {
    success { echo "Success!" }
    failure { echo "Failed!" }
    always {
        sh "docker rmi ${env.FULL_IMAGE_NAME} || true"
        sh "docker logout ${env.DOCKER_REGISTRY} || true"
        cleanWs()
    }
}
```

**Mới:**
```groovy
// Tự động handle bởi cleanup() function
cleanupImages: true
```

## 🎯 Stages tự động thêm trong Shared Library

| # | Stage | Mô tả |
|---|-------|-------|
| 1 | Validation | Kiểm tra required parameters |
| 2 | Confirmation | Xác nhận trước khi deploy prod (chỉ prod) |
| 3 | Checkout | Clone code từ Git |
| 4 | Prepare Environment | Tạo .env file (nếu có) |
| 5 | Build Image | Build Docker image |
| 6 | Push Image | Push image lên registry |
| 7 | Deploy | Deploy lên K8s |
| 8 | Post Deploy Verification | Health check (có thể skip) |
| - | Declarative: Post Actions | Cleanup (auto) |

## ✅ Ưu điểm của Shared Library

### 1. Code ngắn gọn hơn
- **Cũ:** 120 dòng
- **Mới:** 30 dòng (giảm 75%)

### 2. Validation tự động
```
✅ Kiểm tra required parameters
✅ Validate registry format
✅ Validate kubeconfigPath tồn tại
✅ Error messages rõ ràng
```

### 3. Dễ maintain
- Sửa 1 lần ở library → áp dụng cho tất cả pipelines
- Không cần copy-paste code giống nhau

### 4. Tính năng mở rộng
- Health check sau deploy
- Automatic versioning
- Git commit info trong image
- Build timestamps
- Cleanup tự động

### 5. Flexible
- Có thể override bất kỳ config nào
- Có thể skip stages không cần (skipDeploy, skipHealthCheck)
- Support nhiều environments (dev/staging/prod)

## 🚀 Migration Steps

### Bước 1: Copy pipeline mới
```bash
cp examples/Jenkinsfile.coreweb-v2-dev YOUR_JENKINS_JOB_CONFIG
```

### Bước 2: Customize nếu cần
- Thay đổi registry URL
- Thay đổi kubeconfig path
- Thay đổi branch
- Thay đổi .env content

### Bước 3: Test
- Run Jenkins job
- Verify build success
- Verify deployment

### Bước 4: Cleanup old pipeline
- Backup old Jenkinsfile
- Remove old pipeline code

## 📌 Lưu ý

### Về 2 stage "thừa" user nhận thấy:

1. **"Post Deploy Verification"**
   - Đây là health check stage (optional)
   - Có thể skip: `skipHealthCheck: true`

2. **"Declarative: Post Actions"**
   - Stage mặc định của Jenkins
   - Handle post block (cleanup, notifications)
   - Không thể remove (Jenkins behavior)

### Nếu muốn ít stages hơn:
```groovy
buildAndDeploy(
    // ... các params khác ...
    
    skipHealthCheck: true,  // Bỏ "Post Deploy Verification"
    cleanupImages: false    // Bỏ cleanup trong post block
)
```

## 🔗 Tài liệu tham khảo

- [README.md](./README.md) - Full documentation
- [EXAMPLES.md](./EXAMPLES.md) - Các ví dụ khác
- [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md) - Hướng dẫn migrate chi tiết
