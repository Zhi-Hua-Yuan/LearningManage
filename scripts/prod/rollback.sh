#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 1 ]] || lm_die "usage: rollback.sh BACKEND_SHA-FRONTEND_SHA"
[[ "$1" =~ ^[0-9a-f]{40}-[0-9a-f]{40}$ ]] || lm_die "invalid release directory name"

for command in curl docker jq realpath diff ln mv; do
    lm_require_command "$command"
done
lm_acquire_operation_lock
lm_require_private_file "$LM_ENV_FILE"

current_dir="$(realpath -e "$LM_CURRENT_LINK")"
target_dir="$(realpath -e "${LM_RELEASE_ROOT}/$1")"
root_real="$(realpath -e "$LM_RELEASE_ROOT")"
[[ "$target_dir" == "$root_real"/* ]] || lm_die "rollback target escaped release root"
[[ "$target_dir" != "$current_dir" ]] || lm_die "rollback target is already current"

"${target_dir}/scripts/prod/verify-release-bundle.sh"
diff \
    <(jq -S '.migrations' "${current_dir}/production-release-manifest.json") \
    <(jq -S '.migrations' "${target_dir}/production-release-manifest.json") >/dev/null \
    || lm_die "database migration sets differ; automatic rollback refused"
diff \
    <(jq -S '.externalImages' "${current_dir}/production-release-manifest.json") \
    <(jq -S '.externalImages' "${target_dir}/production-release-manifest.json") >/dev/null \
    || lm_die "external image inventories differ; automatic rollback refused"

target_backend="$(jq -er '.applicationImages.backend' "${target_dir}/production-release-manifest.json")"
target_frontend="$(jq -er '.applicationImages.frontend' "${target_dir}/production-release-manifest.json")"
if ! docker image inspect "$target_backend" "$target_frontend" >/dev/null 2>&1; then
    bash "${target_dir}/scripts/prod/build-release-images.sh"
fi
docker image inspect "$target_backend" "$target_frontend" >/dev/null \
    || lm_die "rollback application images are unavailable"

current_backend="$(jq -er '.applicationImages.backend' "${current_dir}/production-release-manifest.json")"
current_frontend="$(jq -er '.applicationImages.frontend' "${current_dir}/production-release-manifest.json")"
BACKEND_IMAGE="$current_backend" FRONTEND_IMAGE="$current_frontend" \
AI_AGENT_TOOL_CALLING_ENABLED=false AI_AGENT_ENABLED=false AI_AGENT_WORKER_ENABLED=false \
AI_RAG_ENABLED=false AI_KNOWLEDGE_WORKER_ENABLED=false \
AI_CLEANUP_ENABLED=false AI_CLEANUP_SCHEDULE_ENABLED=false \
    docker compose --project-name "$LM_PROJECT_NAME" --env-file "$LM_ENV_FILE" \
    --file "${current_dir}/deploy/docker-compose.prod.yml" up -d --no-deps --force-recreate backend

BACKEND_IMAGE="$target_backend" FRONTEND_IMAGE="$target_frontend" \
AI_AGENT_TOOL_CALLING_ENABLED=false AI_AGENT_ENABLED=false AI_AGENT_WORKER_ENABLED=false \
AI_RAG_ENABLED=false AI_KNOWLEDGE_WORKER_ENABLED=false \
AI_CLEANUP_ENABLED=false AI_CLEANUP_SCHEDULE_ENABLED=false \
    docker compose --project-name "$LM_PROJECT_NAME" --env-file "$LM_ENV_FILE" \
    --file "${target_dir}/deploy/docker-compose.prod.yml" up -d --no-deps --force-recreate backend frontend

lm_wait_for_url http://127.0.0.1:18080/api/health '"code":0' 45
bash "${target_dir}/scripts/prod/smoke-test.sh"
env_backup="$(mktemp "${LM_ENV_FILE}.rollback.XXXXXX")"
cp "$LM_ENV_FILE" "$env_backup"
rollback_committed=false
restore_environment_on_failure() {
    if [[ "$rollback_committed" == false && -f "$env_backup" ]]; then
        chmod 0640 "$env_backup"
        chgrp lmdeploy "$env_backup"
        mv -f "$env_backup" "$LM_ENV_FILE"
    fi
}
trap restore_environment_on_failure EXIT
lm_sync_release_environment "$LM_ENV_FILE" "${target_dir}/production-release-manifest.json"
next_link="${LM_CURRENT_LINK}.next"
ln -sfn "$target_dir" "$next_link"
mv -Tf "$next_link" "$LM_CURRENT_LINK"
rollback_committed=true
rm -f -- "$env_backup"
trap - EXIT

lm_log "application rollback completed; database and data services were not restarted"
