#!/usr/bin/env groovy
/**
 * GitOps deploy: thay vì setImage trực tiếp lên cluster, commit tag image mới vào repo GitOps.
 * Argo CD (đọc repo đó) sẽ áp dụng lên cluster.
 *
 * Bật bằng: deploymentType: 'gitops' và
 *   gitops: [
 *     repo: 'git@github.com:ORG/gitops.git',   // SSH URL
 *     branch: 'main',                          // mặc định main
 *     path: 'prod/apps/<service>',             // thư mục có kustomization.yaml
 *     credentialsId: '<id credential SSH có quyền ghi>'
 *   ]
 * Image đổi tag = <registry>/<imageName> (cùng tên với images[].name trong kustomization.yaml).
 */
def call(Map config, Map pipelineConfig) {
    def g = config.gitops ?: [:]
    ['repo', 'path', 'credentialsId'].each { k ->
        if (!g[k]) { error "gitops.${k} is required when deploymentType is 'gitops'" }
    }
    def workDir = "gitops-${env.BUILD_NUMBER}"
    def vars = [
        "G_REPO=${g.repo}",
        "G_BRANCH=${g.branch ?: 'main'}",
        "G_PATH=${g.path}",
        "G_IMAGE=${pipelineConfig.registry}/${pipelineConfig.imageName}",
        "G_TAG=${pipelineConfig.version}",
        "G_APP=${config.appName}",
        "G_DIR=${workDir}"
    ]
    echo "GitOps: ${g.path} -> ${pipelineConfig.registry}/${pipelineConfig.imageName}:${pipelineConfig.version}"
    try {
        withCredentials([sshUserPrivateKey(credentialsId: g.credentialsId, keyFileVariable: 'GITOPS_KEY')]) {
            withEnv(vars) {
                sh '''
                    set -eu
                    export GIT_SSH_COMMAND="ssh -i $GITOPS_KEY -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/tmp/gitops_known_hosts"
                    rm -rf "$G_DIR"
                    git clone -q --depth 1 --branch "$G_BRANCH" "$G_REPO" "$G_DIR"
                    cd "$G_DIR"
                    F="$G_PATH/kustomization.yaml"
                    [ -f "$F" ] || { echo "Không thấy $F trong repo GitOps"; exit 1; }
                    awk -v img="$G_IMAGE" -v tag="$G_TAG" '
                        index($0, "name: " img) > 0 { f = 1 }
                        f && /newTag:/ { sub(/newTag:.*/, "newTag: " tag); f = 0; done = 1 }
                        { print }
                        END { if (!done) exit 3 }' "$F" > "$F.new" || { echo "Không thấy image $G_IMAGE trong $F"; exit 1; }
                    mv "$F.new" "$F"
                    if git diff --quiet; then
                        echo "Tag đã là $G_TAG, không có gì để commit"
                        exit 0
                    fi
                    git -c user.name=jenkins-ci -c user.email=jenkins-ci@clickai.local commit -qam "deploy($G_APP): $G_TAG" -m "build: ${BUILD_URL:-n/a}"
                    ok=0
                    for i in 1 2 3; do
                        if git push -q origin "HEAD:$G_BRANCH"; then ok=1; break; fi
                        git pull -q --rebase origin "$G_BRANCH"
                    done
                    [ "$ok" = 1 ] || { echo "Push vào repo GitOps thất bại sau 3 lần"; exit 1; }
                    git log -1 --oneline
                '''
            }
        }
    } finally {
        sh "rm -rf '${workDir}'"
    }
    echo "GitOps commit xong. Argo CD sẽ áp dụng theo syncPolicy của app (thủ công: cần bấm Sync)."
}
