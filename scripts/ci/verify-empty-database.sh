#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

run_flyway() {
    local action="$1"
    local output
    if ! output="$("${project_root}/scripts/flyway-admin.sh" "$action" 2>&1)"; then
        ci_fail "flyway_${action}_failed"
    fi
    printf '%s\n' "$output"
}

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_assert_ci_app_identity
ci_require_command "grep"
ci_require_command "mysql"
[[ "${DB_NAME}" == "${CI_EMPTY_DB_NAME:-}" ]] || ci_fail "empty_database_name_mismatch"
[[ "${FLYWAY_BASELINE_AUTHORIZED:-false}" != "true" ]] \
    || ci_fail "baseline_authorization_must_not_be_global"

initial_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
ci_assert_equals "0" "$initial_tables" "empty_database_not_empty"

info_before="$(run_flyway info)"
grep -Fq 'info.current=<none>' <<<"$info_before" || ci_fail "empty_info_current_unexpected"
grep -Fq 'info.migration=1|baseline schema|PENDING' <<<"$info_before" \
    || ci_fail "empty_v1_not_pending"

migrate_first="$(run_flyway migrate)"
grep -Fq 'migrate.success=true' <<<"$migrate_first" || ci_fail "empty_first_migrate_failed"
grep -Fq 'migrate.migrationsExecuted=1' <<<"$migrate_first" \
    || ci_fail "empty_first_migrate_count_unexpected"

validate_output="$(run_flyway validate)"
grep -Fq 'validate.success=true' <<<"$validate_output" || ci_fail "empty_validate_failed"

info_after="$(run_flyway info)"
grep -Fq 'info.current=1' <<<"$info_after" || ci_fail "empty_info_current_after_unexpected"
grep -Fq 'info.migration=1|baseline schema|SUCCESS' <<<"$info_after" \
    || ci_fail "empty_v1_not_successful"

migrate_second="$(run_flyway migrate)"
grep -Fq 'migrate.success=true' <<<"$migrate_second" || ci_fail "empty_second_migrate_failed"
grep -Fq 'migrate.migrationsExecuted=0' <<<"$migrate_second" \
    || ci_fail "empty_second_migrate_not_idempotent"

business_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name <> 'flyway_schema_history';")"
all_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
history_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1;")"
history_total="$(ci_mysql_migrator --database="${DB_NAME}" --execute='SELECT COUNT(*) FROM flyway_schema_history;')"
business_rows="$(ci_business_row_total "${DB_NAME}")"
assignee_column="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${DB_NAME}' AND table_name='task' AND column_name='assignee_id';")"
assignee_index="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${DB_NAME}' AND table_name='task' AND index_name='idx_task_assignee_id';")"
status_constraint="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='${DB_NAME}' AND table_name='task' AND constraint_name='chk_task_status_range' AND constraint_type='CHECK';")"

ci_assert_equals "20" "$business_tables" "empty_business_table_count_unexpected"
ci_assert_equals "21" "$all_tables" "empty_total_table_count_unexpected"
ci_assert_equals "1" "$history_rows" "empty_v1_history_missing"
ci_assert_equals "1" "$history_total" "empty_history_row_count_unexpected"
ci_assert_equals "0" "$business_rows" "empty_business_rows_unexpected"
ci_assert_equals "1" "$assignee_column" "empty_assignee_column_missing"
ci_assert_equals "1" "$assignee_index" "empty_assignee_index_missing"
ci_assert_equals "1" "$status_constraint" "empty_status_constraint_missing"
ci_assert_application_ddl_denied

ci_emit "empty.verify.success" "true"
ci_emit "empty.business_tables" "$business_tables"
ci_emit "empty.total_tables" "$all_tables"
ci_emit "empty.business_rows" "$business_rows"
ci_emit "empty.second_migrations_executed" "0"
