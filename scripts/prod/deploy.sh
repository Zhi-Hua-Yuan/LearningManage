#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

initialize_database=false
if [[ $# -eq 1 && "$1" == --initialize-database ]]; then
    initialize_database=true
elif [[ $# -ne 0 ]]; then
    lm_die "usage: deploy.sh [--initialize-database]"
fi

cleanup_initial_migration_env() {
    local migration_real
    [[ "$initialize_database" == true ]] || return 0
    migration_real="$(realpath -m "$LM_MIGRATION_ENV_FILE")"
    if [[ "$migration_real" == /etc/learning-manage/migration.env ]]; then
        rm -f -- "$migration_real"
    fi
}
trap cleanup_initial_migration_env EXIT

for command in curl docker jq realpath ln mv; do
    lm_require_command "$command"
done
lm_acquire_operation_lock
lm_validate_release_dir
lm_require_private_file "$LM_ENV_FILE"
mysql_root_password_file="$(lm_env_value "$LM_ENV_FILE" MYSQL_ROOT_PASSWORD_FILE)"
lm_require_private_file "$mysql_root_password_file"
secret_arguments=()
[[ "$initialize_database" == true ]] && secret_arguments+=(--with-migration)
"${script_dir}/verify-production-secrets.sh" "${secret_arguments[@]}"
"${script_dir}/verify-release-bundle.sh"
lm_export_application_images

manifest="${LM_RELEASE_DIR}/production-release-manifest.json"
[[ "$(lm_env_value "$LM_ENV_FILE" BACKEND_SHA)" == "$(jq -er '.backendSha' "$manifest")" ]] \
    || lm_die "BACKEND_SHA in learning.env does not match this release"
[[ "$(lm_env_value "$LM_ENV_FILE" FRONTEND_SHA)" == "$(jq -er '.frontendSha' "$manifest")" ]] \
    || lm_die "FRONTEND_SHA in learning.env does not match this release"

for cleanup_flag in AI_CLEANUP_ENABLED AI_CLEANUP_SCHEDULE_ENABLED; do
    [[ "$(lm_env_value "$LM_ENV_FILE" "$cleanup_flag")" == false ]] \
        || lm_die "$cleanup_flag must be false for a new deployment"
done

"${script_dir}/build-release-images.sh"
lm_compose up -d mysql redis qdrant

if [[ "$initialize_database" == true ]]; then
    "${script_dir}/provision-database.sh"
    "${script_dir}/migrate.sh"
fi

history_total="$(lm_compose exec -T mysql sh -eu -c '
    MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    export MYSQL_PWD
    mysql -N -B -uroot --protocol=socket "${MYSQL_DATABASE}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1;"
')"
[[ "$history_total" == 9 ]] || lm_die "Flyway V1-V9 are not fully applied"

lm_compose up -d backend frontend
lm_wait_for_url http://127.0.0.1:18080/api/health '"code":0' 45
lm_compose exec -T backend sh -c \
    "wget -q -O - http://127.0.0.1:9123/actuator/health/readiness | grep -q '\"status\":\"UP\"'"
"${script_dir}/smoke-test.sh"

next_link="${LM_CURRENT_LINK}.next"
ln -sfn "$LM_RELEASE_DIR" "$next_link"
mv -Tf "$next_link" "$LM_CURRENT_LINK"

lm_log "Release deployed; current now points to $LM_RELEASE_DIR"
