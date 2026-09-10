#!/usr/bin/env bash

set -Eeuo pipefail

LM_SCRIPT_DIR="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
LM_RELEASE_DIR="$(cd -P -- "${LM_SCRIPT_DIR}/../.." && pwd -P)"
LM_COMPOSE_FILE="${LM_COMPOSE_FILE:-${LM_RELEASE_DIR}/deploy/docker-compose.prod.yml}"
LM_ENV_FILE="${LM_ENV_FILE:-/etc/learning-manage/learning.env}"
LM_MIGRATION_ENV_FILE="${LM_MIGRATION_ENV_FILE:-/etc/learning-manage/migration.env}"
LM_OBSERVABILITY_ENV_FILE="${LM_OBSERVABILITY_ENV_FILE:-/etc/learning-manage/observability.env}"
LM_BACKUP_ENV_FILE="${LM_BACKUP_ENV_FILE:-/etc/learning-manage/backup.env}"
LM_RELEASE_ROOT="${LM_RELEASE_ROOT:-/opt/learning-manage/releases}"
LM_CURRENT_LINK="${LM_CURRENT_LINK:-/opt/learning-manage/current}"
LM_PROJECT_NAME="learning-manage"
LM_OPERATION_LOCK_FILE="${LM_OPERATION_LOCK_FILE:-/opt/learning-manage/.operation.lock}"

lm_log() {
    printf '[learning-manage] %s\n' "$*"
}

lm_die() {
    printf '[learning-manage] ERROR: %s\n' "$*" >&2
    exit 1
}

lm_require_command() {
    command -v "$1" >/dev/null 2>&1 || lm_die "missing required command: $1"
}

lm_require_file() {
    [[ -f "$1" ]] || lm_die "required file not found: $1"
}

lm_require_private_file() {
    local file="$1" mode owner group
    lm_require_file "$file"
    mode="$(stat -c '%a' "$file")"
    if [[ "${LM_ALLOW_TEST_FILE_MODES:-false}" == true && "$file" == /tmp/* ]]; then
        return 0
    fi
    [[ "$mode" == 600 || "$mode" == 640 ]] \
        || lm_die "protected file must use mode 0600 or 0640: $file has $mode"
    owner="$(stat -c '%U' "$file")"
    group="$(stat -c '%G' "$file")"
    [[ "$owner" == root || "$owner" == lmdeploy ]] \
        || lm_die "protected file must be owned by root or lmdeploy: $file"
    if [[ "$mode" == 640 ]]; then
        [[ "$group" == lmdeploy ]] || lm_die "mode-0640 protected file must use group lmdeploy: $file"
    fi
}

lm_env_value() {
    local file="$1" name="$2" value
    lm_require_file "$file"
    value="$(awk -v key="$name" '
        index($0, key "=") == 1 { value = substr($0, length(key) + 2) }
        END { print value }
    ' "$file")"
    [[ -n "$value" ]] || lm_die "$name is missing or empty in $file"
    printf '%s' "$value"
}

lm_acquire_operation_lock() {
    if [[ "${LM_OPERATION_LOCK_HELD:-false}" == true ]]; then
        return 0
    fi
    lm_require_command flock
    mkdir -p "$(dirname -- "$LM_OPERATION_LOCK_FILE")"
    exec {LM_OPERATION_LOCK_FD}>"$LM_OPERATION_LOCK_FILE"
    flock --exclusive --wait 300 "$LM_OPERATION_LOCK_FD" \
        || lm_die "another LearningManage operation still holds the deployment lock"
    export LM_OPERATION_LOCK_HELD=true
}

lm_sync_release_environment() {
    local env_file="$1" manifest="$2" work_file next_file key value count
    local -a keys values
    lm_require_private_file "$env_file"
    lm_require_file "$manifest"
    keys=(
        BACKEND_SHA FRONTEND_SHA BACKEND_IMAGE FRONTEND_IMAGE
        RUNTIME_IMAGE NGINX_IMAGE MYSQL_IMAGE REDIS_IMAGE QDRANT_IMAGE
        PROMETHEUS_IMAGE TEMPO_IMAGE GRAFANA_IMAGE
        AI_KNOWLEDGE_WORKER_ENABLED AI_RAG_ENABLED AI_AGENT_ENABLED
        AI_AGENT_WORKER_ENABLED AI_AGENT_TOOL_CALLING_ENABLED
        AI_CLEANUP_ENABLED AI_CLEANUP_SCHEDULE_ENABLED
    )
    values=(
        "$(jq -er '.backendSha' "$manifest")"
        "$(jq -er '.frontendSha' "$manifest")"
        "$(jq -er '.applicationImages.backend' "$manifest")"
        "$(jq -er '.applicationImages.frontend' "$manifest")"
        "$(jq -er '.externalImages.runtime' "$manifest")"
        "$(jq -er '.externalImages.nginx' "$manifest")"
        "$(jq -er '.externalImages.mysql' "$manifest")"
        "$(jq -er '.externalImages.redis' "$manifest")"
        "$(jq -er '.externalImages.qdrant' "$manifest")"
        "$(jq -er '.externalImages.prometheus' "$manifest")"
        "$(jq -er '.externalImages.tempo' "$manifest")"
        "$(jq -er '.externalImages.grafana' "$manifest")"
        false false false false false false false
    )
    work_file="$(mktemp "${env_file}.sync.XXXXXX")"
    cp "$env_file" "$work_file"
    for ((index = 0; index < ${#keys[@]}; index++)); do
        key="${keys[$index]}"
        value="${values[$index]}"
        count="$(grep -c "^${key}=" "$work_file" || true)"
        if [[ "$count" != 1 ]]; then
            rm -f -- "$work_file"
            lm_die "$env_file must contain exactly one $key entry"
        fi
        next_file="$(mktemp "${env_file}.sync-next.XXXXXX")"
        awk -v target="$key" -v replacement="$value" '
            index($0, target "=") == 1 { print target "=" replacement; next }
            { print }
        ' "$work_file" > "$next_file"
        rm -f -- "$work_file"
        work_file="$next_file"
    done
    chmod 0640 "$work_file"
    chgrp lmdeploy "$work_file"
    mv -f "$work_file" "$env_file"
}

lm_compose() {
    docker compose \
        --project-name "$LM_PROJECT_NAME" \
        --env-file "$LM_ENV_FILE" \
        --file "$LM_COMPOSE_FILE" "$@"
}

lm_compose_admin() {
    lm_require_file "$LM_MIGRATION_ENV_FILE"
    docker compose \
        --project-name "$LM_PROJECT_NAME" \
        --env-file "$LM_ENV_FILE" \
        --env-file "$LM_MIGRATION_ENV_FILE" \
        --file "$LM_COMPOSE_FILE" \
        --profile admin "$@"
}

lm_compose_observability() {
    lm_require_file "$LM_OBSERVABILITY_ENV_FILE"
    docker compose \
        --project-name "$LM_PROJECT_NAME" \
        --env-file "$LM_ENV_FILE" \
        --env-file "$LM_OBSERVABILITY_ENV_FILE" \
        --file "$LM_COMPOSE_FILE" \
        --profile observability "$@"
}

lm_validate_release_dir() {
    local release_real root_real
    release_real="$(realpath -e "$LM_RELEASE_DIR")"
    root_real="$(realpath -e "$LM_RELEASE_ROOT")"
    [[ "$release_real" == "$root_real"/* ]] \
        || lm_die "release directory must be a child of $root_real"
}

lm_export_application_images() {
    local manifest="${1:-${LM_RELEASE_DIR}/production-release-manifest.json}"
    lm_require_command jq
    lm_require_file "$manifest"
    BACKEND_IMAGE="$(jq -er '.applicationImages.backend' "$manifest")"
    FRONTEND_IMAGE="$(jq -er '.applicationImages.frontend' "$manifest")"
    RUNTIME_IMAGE="$(jq -er '.externalImages.runtime' "$manifest")"
    NGINX_IMAGE="$(jq -er '.externalImages.nginx' "$manifest")"
    MYSQL_IMAGE="$(jq -er '.externalImages.mysql' "$manifest")"
    REDIS_IMAGE="$(jq -er '.externalImages.redis' "$manifest")"
    QDRANT_IMAGE="$(jq -er '.externalImages.qdrant' "$manifest")"
    PROMETHEUS_IMAGE="$(jq -er '.externalImages.prometheus' "$manifest")"
    TEMPO_IMAGE="$(jq -er '.externalImages.tempo' "$manifest")"
    GRAFANA_IMAGE="$(jq -er '.externalImages.grafana' "$manifest")"
    export BACKEND_IMAGE FRONTEND_IMAGE RUNTIME_IMAGE NGINX_IMAGE MYSQL_IMAGE REDIS_IMAGE
    export QDRANT_IMAGE PROMETHEUS_IMAGE TEMPO_IMAGE GRAFANA_IMAGE
}

lm_wait_for_url() {
    local url="$1" expected="$2" attempts="${3:-30}" body
    for ((attempt = 1; attempt <= attempts; attempt++)); do
        if body="$(curl --fail --silent --show-error --max-time 5 "$url" 2>/dev/null)" \
                && grep -Fq "$expected" <<<"$body"; then
            return 0
        fi
        sleep 2
    done
    lm_die "timed out waiting for $url"
}
