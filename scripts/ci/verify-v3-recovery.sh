#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"
# shellcheck source=lib/v3-test-common.sh
source "${script_dir}/lib/v3-test-common.sh"

work_dir=""
backup_file=""
structure_backup_file=""
cleanup() {
    if [[ -n "$backup_file" && -f "$backup_file" ]]; then
        rm -f -- "$backup_file"
    fi
    if [[ -n "$structure_backup_file" && -f "$structure_backup_file" ]]; then
        rm -f -- "$structure_backup_file"
    fi
    if [[ -n "$work_dir" && -d "$work_dir" ]]; then
        rmdir -- "$work_dir" 2>/dev/null || true
    fi
}
trap cleanup EXIT

run_flyway_for_database() {
    local action="$1"
    local database_name="$2"
    local target_version="${3:-}"
    local output

    if [[ -n "$target_version" ]]; then
        if ! output="$(
            DB_NAME="$database_name" \
            FLYWAY_EXPECTED_DB_NAME="$database_name" \
            FLYWAY_BASELINE_AUTHORIZED="$([[ "$action" == "baseline" ]] && printf 'true' || printf 'false')" \
            FLYWAY_BASELINE_VERSION="1" \
            FLYWAY_TARGET_VERSION="$target_version" \
            "${v3_project_root}/scripts/flyway-admin.sh" "$action" 2>&1
        )"; then
            printf '%s\n' "$output" >&2
            ci_fail "v3_recovery_flyway_${action}_failed"
        fi
    else
        if ! output="$(
            DB_NAME="$database_name" \
            FLYWAY_EXPECTED_DB_NAME="$database_name" \
            FLYWAY_BASELINE_AUTHORIZED="$([[ "$action" == "baseline" ]] && printf 'true' || printf 'false')" \
            FLYWAY_BASELINE_VERSION="1" \
            "${v3_project_root}/scripts/flyway-admin.sh" "$action" 2>&1
        )"; then
            printf '%s\n' "$output" >&2
            ci_fail "v3_recovery_flyway_${action}_failed"
        fi
    fi
    printf '%s\n' "$output" >&2
    printf '%s\n' "$output"
}

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_require_command "mysql"
ci_require_command "mysqldump"
ci_require_command "mktemp"
ci_require_command "sha256sum"
ci_require_env "CI_LEGACY_DB_NAME"
v3_require_inputs

source_database="${CI_LEGACY_DB_NAME}_v3_recovery_source"
restore_database="${CI_LEGACY_DB_NAME}_v3_recovery_target"
ci_mysql_migrator --database="$source_database" <"$v3_legacy_fixture" >/dev/null \
    || ci_fail "v3_recovery_legacy_fixture_import_failed"
ci_mysql_migrator --database="$source_database" <"$v3_stage1_seed" >/dev/null \
    || ci_fail "v3_recovery_stage1_seed_import_failed"
baseline_output="$(run_flyway_for_database baseline "$source_database")"
grep -Fq 'baseline.success=true' <<<"$baseline_output" || ci_fail "v3_recovery_baseline_failed"
v2_migrate_output="$(run_flyway_for_database migrate "$source_database" 2)"
grep -Fq 'migrate.success=true' <<<"$v2_migrate_output" || ci_fail "v3_recovery_v2_migrate_failed"
grep -Fq 'migrate.migrationsExecuted=1' <<<"$v2_migrate_output" \
    || ci_fail "v3_recovery_v2_migration_count_unexpected"
ci_mysql_migrator --database="$source_database" <"$v3_stage2_seed" >/dev/null \
    || ci_fail "v3_recovery_stage2_seed_import_failed"
v3_create_database "$restore_database"

# Re-assert the isolated recovery grants immediately before Flyway runs. This
# keeps the rehearsal independent from any grant cache or provisioning order.
if ! ci_mysql_admin >/dev/null 2>&1 <<SQL
GRANT ALL PRIVILEGES
    ON \`${source_database}\`.* TO '${FLYWAY_DB_USERNAME}'@'%';
GRANT ALL PRIVILEGES
    ON \`${restore_database}\`.* TO '${FLYWAY_DB_USERNAME}'@'%';
SQL
then
    ci_fail "v3_recovery_grant_refresh_failed"
fi
ci_mysql_migrator --database="$source_database" --execute='CREATE TEMPORARY TABLE `_v3_recovery_privilege_probe` (`id` tinyint); DROP TEMPORARY TABLE `_v3_recovery_privilege_probe`;' >/dev/null \
    || ci_fail "v3_recovery_migrator_privilege_probe_failed"

source_tables_before="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${source_database}';")"
source_rows_before="$(ci_business_row_total "$source_database")"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/learning-manage-v3-recovery.XXXXXX")"
[[ "$work_dir" == "${TMPDIR:-/tmp}/learning-manage-v3-recovery."* ]] \
    || ci_fail "v3_recovery_temporary_directory_unexpected"
backup_file="${work_dir}/v2-before-v3.sql"
structure_backup_file="${work_dir}/v2-before-v3-structure.sql"

if ! MYSQL_PWD="${FLYWAY_DB_PASSWORD}" mysqldump \
    --protocol=TCP --host="${DB_HOST}" --port="${DB_PORT}" \
    --user="${FLYWAY_DB_USERNAME}" --single-transaction --skip-lock-tables \
    --skip-add-locks --no-tablespaces --set-gtid-purged=OFF \
    "$source_database" >"$backup_file"; then
    ci_fail "v3_recovery_backup_failed"
fi
[[ -s "$backup_file" ]] || ci_fail "v3_recovery_backup_empty"
backup_sha256="$(sha256sum "$backup_file" | awk '{print toupper($1)}')"

if ! MYSQL_PWD="${FLYWAY_DB_PASSWORD}" mysqldump \
    --protocol=TCP --host="${DB_HOST}" --port="${DB_PORT}" \
    --user="${FLYWAY_DB_USERNAME}" --no-data --skip-lock-tables \
    --no-tablespaces --set-gtid-purged=OFF \
    "$source_database" >"$structure_backup_file"; then
    ci_fail "v3_recovery_structure_backup_failed"
fi
[[ -s "$structure_backup_file" ]] || ci_fail "v3_recovery_structure_backup_empty"
structure_backup_sha256="$(sha256sum "$structure_backup_file" | awk '{print toupper($1)}')"

preflight_output="$(ci_mysql_migrator --database="$source_database" <"$v3_preflight")" \
    || ci_fail "v3_recovery_preflight_failed"
v3_assert_preflight "$preflight_output" "0" "0" "v3_recovery_preflight"
v3_migrate_output="$(run_flyway_for_database migrate "$source_database")"
grep -Fq 'migrate.success=true' <<<"$v3_migrate_output" || ci_fail "v3_recovery_migration_failed"
grep -Fq 'migrate.migrationsExecuted=1' <<<"$v3_migrate_output" \
    || ci_fail "v3_recovery_migration_count_unexpected"

if ! ci_mysql_migrator --database="$restore_database" <"$backup_file" >/dev/null 2>&1; then
    ci_fail "v3_recovery_restore_failed"
fi

restored_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${restore_database}';")"
restored_rows="$(ci_business_row_total "$restore_database")"
restored_v3_columns="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${restore_database}' AND table_name='ai_call_log' AND column_name='requested_model';")"
restored_archive="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${restore_database}' AND table_name='ai_draft_confirm_log_archive';")"
restored_old_unique="$(ci_mysql_migrator --execute="SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${restore_database}' AND table_name='ai_draft_confirm_log' AND index_name='uk_user_draft_op';")"

ci_assert_equals "$source_tables_before" "$restored_tables" "v3_recovery_table_count_mismatch"
ci_assert_equals "$source_rows_before" "$restored_rows" "v3_recovery_row_count_mismatch"
ci_assert_equals "0" "$restored_v3_columns" "v3_recovery_v3_columns_present"
ci_assert_equals "0" "$restored_archive" "v3_recovery_archive_present"
ci_assert_equals "1" "$restored_old_unique" "v3_recovery_old_unique_missing"

ci_emit "v3.recovery.verify.success" "true"
ci_emit "v3.recovery.backup.sha256" "$backup_sha256"
ci_emit "v3.recovery.structure_backup.sha256" "$structure_backup_sha256"
ci_emit "v3.recovery.restored.tables" "$restored_tables"
ci_emit "v3.recovery.restored.rows" "$restored_rows"
