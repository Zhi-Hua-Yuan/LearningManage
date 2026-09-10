#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

cleanup_migration_env() {
    local migration_real
    migration_real="$(realpath -m "$LM_MIGRATION_ENV_FILE")"
    if [[ "$migration_real" == /etc/learning-manage/migration.env ]]; then
        rm -f -- "$migration_real"
        lm_log "removed temporary migration environment"
    else
        lm_log "custom migration environment retained: $migration_real"
    fi
}
trap cleanup_migration_env EXIT

lm_require_command docker
lm_acquire_operation_lock
lm_require_private_file "$LM_ENV_FILE"
lm_require_private_file "$LM_MIGRATION_ENV_FILE"
"${script_dir}/verify-production-secrets.sh" --with-migration
"${script_dir}/verify-release-bundle.sh"
lm_export_application_images

migrator_user="$(lm_env_value "$LM_MIGRATION_ENV_FILE" FLYWAY_DB_USERNAME)"
[[ "$migrator_user" == learning_manage_migrator ]] || lm_die "unexpected migrator database username"

lm_compose_admin run --rm migrator info
lm_compose_admin run --rm -e FLYWAY_ALLOW_PENDING_VALIDATE=true migrator validate
lm_compose_admin run --rm migrator migrate
lm_compose_admin run --rm migrator validate
lm_compose_admin run --rm migrator info

history_summary="$(lm_compose exec -T mysql sh -eu -c '
    MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    export MYSQL_PWD
    mysql -N -B -uroot --protocol=socket "${MYSQL_DATABASE}" --execute="
        SELECT COUNT(*),
               SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END),
               SUM(CASE WHEN version REGEXP '\''^[1-8]$'\'' AND type = '\''SQL'\'' AND success = 1 THEN 1 ELSE 0 END)
        FROM flyway_schema_history;"
')"
[[ "$history_summary" == $'8\t8\t8' ]] || lm_die "unexpected Flyway history summary: $history_summary"

app_user="$(lm_env_value "$LM_ENV_FILE" DB_USERNAME)"
app_password="$(lm_env_value "$LM_ENV_FILE" DB_PASSWORD)"
db_name="$(lm_env_value "$LM_ENV_FILE" DB_NAME)"
lm_compose exec -T -e "MYSQL_PWD=$app_password" mysql \
    mysql -N -B -h127.0.0.1 -u"$app_user" "$db_name" --execute='SELECT 1;' \
    | grep -qx 1 || lm_die "application account cannot perform connection check"
if lm_compose exec -T -e "MYSQL_PWD=$app_password" mysql \
        mysql -h127.0.0.1 -u"$app_user" "$db_name" \
        --execute='CREATE TABLE __lm_privilege_probe (id INT PRIMARY KEY);' >/dev/null 2>&1; then
    lm_compose exec -T mysql sh -eu -c '
        MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
        export MYSQL_PWD
        mysql -uroot --protocol=socket "${MYSQL_DATABASE}" --execute="DROP TABLE IF EXISTS __lm_privilege_probe;"
    '
    lm_die "application account unexpectedly has DDL permission"
fi

lm_log "Flyway V1-V8 migration and application privilege checks passed"
