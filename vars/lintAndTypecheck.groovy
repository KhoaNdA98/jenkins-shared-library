#!/usr/bin/env groovy
/**
 * Run lint and type-check inside a lightweight Node container,
 * BEFORE the Docker image build stage. Fails fast so we never
 * waste BuildKit time on bad code.
 *
 * Supported options in config:
 *   - lintAndTypecheck  : boolean – enable this stage (default: false)
 *   - lintScript        : shell command to run lint (default: 'yarn lint')
 *   - typecheckScript   : shell command to run typecheck (default: 'yarn tsc --noEmit')
 *   - nodeImage         : Docker image for the runner (default: 'node:20.14-alpine')
 *   - skipTypecheck     : boolean – skip typecheck, only run lint (default: false)
 *   - skipLint          : boolean – skip lint, only run typecheck (default: false)
 *   - lintCacheDir      : host path to mount as .next/cache for lint speed (optional)
 */
def call(Map config) {
    def nodeImage      = config.nodeImage      ?: 'node:20.14-alpine'
    def lintScript     = config.lintScript     ?: 'yarn lint'
    def typecheckScript = config.typecheckScript ?: 'yarn tsc --noEmit'
    def skipLint       = config.skipLint       == true
    def skipTypecheck  = config.skipTypecheck  == true

    echo "🔍 Running quality checks (lint + typecheck) on: ${nodeImage}"
    echo "   Lint    : ${skipLint       ? 'SKIPPED' : lintScript}"
    echo "   Typecheck: ${skipTypecheck ? 'SKIPPED' : typecheckScript}"

    // Reuse node_modules from workspace if present (already checked-out).
    // Jenkins workspace is mounted at /workspace inside the container.
    docker.image(nodeImage).inside('--user=root') {
        try {
            // Install deps only when node_modules is absent (cold start)
            sh '''
                if [ ! -d node_modules ]; then
                    echo "📦 node_modules not found – running yarn install (frozen)..."
                    yarn install --frozen-lockfile --prefer-offline
                else
                    echo "📦 node_modules found – skipping yarn install"
                fi
            '''

            if (!skipLint) {
                echo "▶️  Running lint..."
                sh "${lintScript}"
                echo "✅ Lint passed"
            }

            if (!skipTypecheck) {
                echo "▶️  Running typecheck..."
                sh "${typecheckScript}"
                echo "✅ Typecheck passed"
            }

        } catch (Exception e) {
            error "❌ Quality check failed. Fix lint/type errors before building. Details: ${e.message}"
        }
    }

    echo "✅ All quality checks passed – proceeding to Build Image"
}
