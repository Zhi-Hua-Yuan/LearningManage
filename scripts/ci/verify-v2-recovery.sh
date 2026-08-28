#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
legacy_fixture="${project_root}/src/test/resources/db/legacy/pre_flyway_v1_schema.sql"
seed_fixture="${project_root}/src/test/resources/db/stage1/v1_to_v2_seed.sql"
preflight_sql="${project_root}/sql/flyway/stage1/01_preflight_v2.sql"
post_verify_sql="${project_root}/sql/flyway/stage1/02_post_verify_v2.sql"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

work_dir=""
backup_file=""

cleanup() {
    if [[ -n "$backup_file" && -f "$backup_file" ]]; then
        rm -f -- "$backup_file"
    fi
    if [[ -n "$work_dir" && -d "$work_dir" ]]; then
        rmdir -- "$work_dir" 2>/dev/null || true
    fi
}

run_flyway_for_database() {
    local action="$1"
    local database_name="$2"
    local output

    if ! output="$(
        DB_NAME="$database_name" \
        FLYWAY_EXPECTED_DB_NAME="$database_name" \
        FLYWAY_BASELINE_AUTHORIZED="$([[ "$action" == "baseline" ]] && printf 'true' || printf 'false')" \
        FLYWAY_BASELINE_VERSION="1" \
        "${project_root}/scripts/flyway-admin.sh" "$action" 2>&1
    )"; then
        printf '%s\n' "$output" >&2
        ci_fail "recovery_flyway_${action}_failed"
    fi
    printf '%s\n' "$output" >&2
    printf '%s\n' "$output"
}

trap cleanup EXIT

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_require_command "awk"
ci_require_command "mysql"
ci_require_command "mysqldump"
ci_require_command "mktemp"
ci_require_command "sha256sum"
ci_require_env "CI_LEGACY_DB_NAME"

source_database="${CI_LEGACY_DB_NAME}_recovery_source"
restore_database="${CI_LEGACY_DB_NAME}_recovery_target"
ci_assert_ci_database_name "$source_database"
ci_assert_ci_database_name "$restore_database"
[[ "$source_database" != "$restore_database" ]] || ci_fail "recovery_database_names_must_differ"

for required_file in "$legacy_fixture" "$seed_fixture" "$preflight_sql" "$post_verify_sql"; do
    [[ -f "$required_file" ]] || ci_fail "recovery_input_missing"
done

if ! ci_mysql_admin >/dev/null 2>&1 <<SQL
CREATE DATABASE \`${source_database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE \`${restore_database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON \`${source_database}\`.* TO '${FLYWAY_DB_USERNAME}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON \`${restore_database}\`.* TO '${FLYWAY_DB_USERNAME}'@'%';
SQL
then
    ci_fail "recovery_database_provision_failed"
fi

ci_mysql_migrator --database="$source_database" <"$legacy_fixture" >/dev/null \
    || ci_fail "recovery_legacy_fixture_import_failed"
ci_mysql_migrator --database="$source_database" <"$seed_fixture" >/dev/null \
    || ci_fail "recovery_seed_fixture_import_failed"

baseline_output="$(run_flyway_for_database baseline "$source_database")"
grep -Fq 'baseline.success=true' <<<"$baseline_output" || ci_fail "recovery_baseline_failed"

source_tables_before="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${source_database}' AND table_name <> 'flyway_schema_history';")"
source_rows_before="$(ci_business_row_total "$source_database")"
source_history_before="$(ci_mysql_migrator --database="$source_database" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND type='BASELINE' AND success=1;")"
ci_assert_equals "20" "$source_tables_before" "recovery_source_v1_table_count_unexpected"
ci_assert_equals "20" "$source_rows_before" "recovery_source_v1_row_count_unexpected"
ci_assert_equals "1" "$source_history_before" "recovery_source_v1_history_missing"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/learning-manage-v2-recovery.XXXXXX")"
[[ "$work_dir" == "${TMPDIR:-/tmp}/learning-manage-v2-recovery."* ]] \
    || ci_fail "recovery_temporary_directory_unexpected"
backup_file="${work_dir}/legacy-v1.sql"

if ! MYSQL_PWD="${FLYWAY_DB_PASSWORD}" mysqldump \
    --protocol=TCP \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${FLYWAY_DB_USERNAME}" \
    --single-transaction \
    --skip-lock-tables \
    --skip-add-locks \
    --no-tablespaces \
    --set-gtid-purged=OFF \
    "$source_database" >"$backup_file"; then
    ci_fail "recovery_backup_failed"
fi
[[ -s "$backup_file" ]] || ci_fail "recovery_backup_empty"
backup_sha256="$(sha256sum "$backup_file" | awk '{print toupper($1)}')"
[[ "$backup_sha256" =~ ^[A-F0-9]{64}$ ]] || ci_fail "recovery_backup_checksum_invalid"

preflight_output="$(ci_mysql_migrator --database="$source_database" <"$preflight_sql")" \
    || ci_fail "recovery_preflight_failed"
ci_assert_stage1_check_output "$preflight_output" '^V2-P-' "25" "recovery_preflight"

migrate_output="$(run_flyway_for_database migrate "$source_database")"
grep -Fq 'migrate.success=true' <<<"$migrate_output" || ci_fail "recovery_migrate_failed"
grep -Fq 'migrate.migrationsExecuted=1' <<<"$migrate_output" \
    || ci_fail "recovery_migration_count_unexpected"

post_verify_output="$(ci_mysql_migrator --database="$source_database" <"$post_verify_sql")" \
    || ci_fail "recovery_post_verify_failed"
ci_assert_stage1_check_output "$post_verify_output" '^V2-V-' "12" "recovery_post_verify"

if ! ci_mysql_migrator --database="$restore_database" <"$backup_file" >/dev/null 2>&1; then
    ci_fail "recovery_restore_failed"
fi

restored_business_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${restore_database}' AND table_name <> 'flyway_schema_history';")"
restored_total_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${restore_database}';")"
restored_rows="$(ci_business_row_total "$restore_database")"
restored_history="$(ci_mysql_migrator --database="$restore_database" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND type='BASELINE' AND success=1;")"
legacy_assignee_column="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${restore_database}' AND table_name='task' AND column_name='assignee_id';")"
v2_assignee_column="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${restore_database}' AND table_name='task' AND column_name='assignee_user_id';")"
v2_assignment_log_table="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${restore_database}' AND table_name='task_assignment_log';")"
v2_review_task_table="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${restore_database}' AND table_name='weekly_review_task';")"

ci_assert_equals "$source_tables_before" "$restored_business_tables" "recovery_restored_table_count_mismatch"
ci_assert_equals "21" "$restored_total_tables" "recovery_restored_total_table_count_unexpected"
ci_assert_equals "$source_rows_before" "$restored_rows" "recovery_restored_row_count_mismatch"
ci_assert_equals "$source_history_before" "$restored_history" "recovery_restored_history_mismatch"
ci_assert_equals "1" "$legacy_assignee_column" "recovery_legacy_assignee_column_missing"
ci_assert_equals "0" "$v2_assignee_column" "recovery_v2_assignee_column_present"
ci_assert_equals "0" "$v2_assignment_log_table" "recovery_v2_assignment_log_present"
ci_assert_equals "0" "$v2_review_task_table" "recovery_v2_review_task_present"

ci_emit "recovery.verify.success" "true"
ci_emit "recovery.backup.sha256" "$backup_sha256"
ci_emit "recovery.source.version" "2"
ci_emit "recovery.restored.version" "1"
ci_emit "recovery.restored.business_tables" "$restored_business_tables"
ci_emit "recovery.restored.business_rows" "$restored_rows"
