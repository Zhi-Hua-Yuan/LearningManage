#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

with_migration=false
with_observability=false
for argument in "$@"; do
    case "$argument" in
        --with-migration) with_migration=true ;;
        --with-observability) with_observability=true ;;
        *) lm_die "usage: verify-production-secrets.sh [--with-migration] [--with-observability]" ;;
    esac
done

lm_require_private_file "$LM_ENV_FILE"

reject_placeholder() {
    local name="$1" value="$2" normalized="${2,,}"
    [[ -n "$value" ]] || lm_die "$name must not be empty"
    [[ "$value" != *[[:space:]]* ]] || lm_die "$name must not contain whitespace"
    case "$normalized" in
        *replace_with*|*please_set*|*changeme*|*default_secret*|*'${'*|password|root|secret)
            lm_die "$name contains a placeholder or forbidden default"
            ;;
    esac
}

require_secret() {
    local name="$1" value="$2" minimum="$3"
    reject_placeholder "$name" "$value"
    ((${#value} >= minimum)) || lm_die "$name must contain at least $minimum characters"
}

require_url_safe_secret() {
    local name="$1" value="$2" minimum="$3"
    require_secret "$name" "$value" "$minimum"
    [[ "$value" =~ ^[A-Za-z0-9._~!@%+=:-]+$ ]] \
        || lm_die "$name must contain only URL-safe characters"
}

read_secret_file() {
    local name="$1" file="$2" nonempty_lines value
    lm_require_private_file "$file"
    nonempty_lines="$(awk 'NF { count++ } END { print count + 0 }' "$file")"
    [[ "$nonempty_lines" == 1 ]] || lm_die "$name file must contain exactly one non-empty line"
    value="$(tr -d '\r\n' < "$file")"
    require_secret "$name" "$value" 32
    printf '%s' "$value"
}

db_password="$(lm_env_value "$LM_ENV_FILE" DB_PASSWORD)"
redis_password="$(lm_env_value "$LM_ENV_FILE" REDIS_PASSWORD)"
jwt_secret="$(lm_env_value "$LM_ENV_FILE" JWT_SECRET)"
aliyun_key="$(lm_env_value "$LM_ENV_FILE" ALIYUN_API_KEY)"
embedding_key="$(lm_env_value "$LM_ENV_FILE" AI_EMBEDDING_API_KEY)"
rerank_key="$(lm_env_value "$LM_ENV_FILE" AI_RERANK_API_KEY)"
rag_hmac="$(lm_env_value "$LM_ENV_FILE" AI_RAG_QUESTION_HMAC_SECRET)"
qdrant_key="$(lm_env_value "$LM_ENV_FILE" QDRANT_API_KEY)"
mysql_root_file="$(lm_env_value "$LM_ENV_FILE" MYSQL_ROOT_PASSWORD_FILE)"
mysql_root_password="$(read_secret_file MYSQL_ROOT_PASSWORD "$mysql_root_file")"

require_url_safe_secret DB_PASSWORD "$db_password" 32
require_url_safe_secret REDIS_PASSWORD "$redis_password" 32
require_secret JWT_SECRET "$jwt_secret" 32
require_secret ALIYUN_API_KEY "$aliyun_key" 16
require_secret AI_EMBEDDING_API_KEY "$embedding_key" 16
require_secret AI_RERANK_API_KEY "$rerank_key" 16
require_secret AI_RAG_QUESTION_HMAC_SECRET "$rag_hmac" 32
require_url_safe_secret QDRANT_API_KEY "$qdrant_key" 32

declare -a protected_names=(MYSQL_ROOT_PASSWORD DB_PASSWORD REDIS_PASSWORD JWT_SECRET AI_RAG_QUESTION_HMAC_SECRET QDRANT_API_KEY)
declare -a protected_values=("$mysql_root_password" "$db_password" "$redis_password" "$jwt_secret" "$rag_hmac" "$qdrant_key")

if [[ "$with_migration" == true ]]; then
    lm_require_private_file "$LM_MIGRATION_ENV_FILE"
    migrator_user="$(lm_env_value "$LM_MIGRATION_ENV_FILE" FLYWAY_DB_USERNAME)"
    [[ "$migrator_user" == learning_manage_migrator ]] || lm_die "unexpected migrator database username"
    migrator_password="$(lm_env_value "$LM_MIGRATION_ENV_FILE" FLYWAY_DB_PASSWORD)"
    require_url_safe_secret FLYWAY_DB_PASSWORD "$migrator_password" 32
    protected_names+=(FLYWAY_DB_PASSWORD)
    protected_values+=("$migrator_password")
fi

if [[ "$with_observability" == true ]]; then
    lm_require_private_file "$LM_OBSERVABILITY_ENV_FILE"
    grafana_file="$(lm_env_value "$LM_OBSERVABILITY_ENV_FILE" GRAFANA_ADMIN_PASSWORD_FILE)"
    grafana_password="$(read_secret_file GRAFANA_ADMIN_PASSWORD "$grafana_file")"
    protected_names+=(GRAFANA_ADMIN_PASSWORD)
    protected_values+=("$grafana_password")
fi

for ((left = 0; left < ${#protected_values[@]}; left++)); do
    for ((right = left + 1; right < ${#protected_values[@]}; right++)); do
        [[ "${protected_values[$left]}" != "${protected_values[$right]}" ]] \
            || lm_die "${protected_names[$left]} and ${protected_names[$right]} must be independent"
    done
done

declare -a provider_names=(ALIYUN_API_KEY AI_EMBEDDING_API_KEY AI_RERANK_API_KEY)
declare -a provider_values=("$aliyun_key" "$embedding_key" "$rerank_key")
for ((provider = 0; provider < ${#provider_values[@]}; provider++)); do
    for ((protected = 0; protected < ${#protected_values[@]}; protected++)); do
        [[ "${provider_values[$provider]}" != "${protected_values[$protected]}" ]] \
            || lm_die "${provider_names[$provider]} must not reuse ${protected_names[$protected]}"
    done
done

lm_log "protected production secret checks passed"
