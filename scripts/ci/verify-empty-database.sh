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
ci_require_command "awk"
[[ "${DB_NAME}" == "${CI_EMPTY_DB_NAME:-}" ]] || ci_fail "empty_database_name_mismatch"
[[ "${FLYWAY_BASELINE_AUTHORIZED:-false}" != "true" ]] \
    || ci_fail "baseline_authorization_must_not_be_global"

initial_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
ci_assert_equals "0" "$initial_tables" "empty_database_not_empty"

info_before="$(run_flyway info)"
grep -Fq 'info.current=<none>' <<<"$info_before" || ci_fail "empty_info_current_unexpected"
grep -Fq 'info.migration=1|baseline schema|PENDING' <<<"$info_before" \
    || ci_fail "empty_v1_not_pending"
grep -Fq 'info.migration=2|stage1 business semantics and permissions|PENDING' <<<"$info_before" \
    || ci_fail "empty_v2_not_pending"
grep -Fq 'info.migration=3|stage2 ai invocation governance|PENDING' <<<"$info_before" \
    || ci_fail "empty_v3_not_pending"
grep -Fq 'info.migration=4|stage4 knowledge index and outbox|PENDING' <<<"$info_before" \
    || ci_fail "empty_v4_not_pending"
grep -Fq 'info.migration=5|stage5 permission aware rag|PENDING' <<<"$info_before" \
    || ci_fail "empty_v5_not_pending"
grep -Fq 'info.migration=6|stage5 qdrant numeric permission payload rebuild|PENDING' <<<"$info_before" \
    || ci_fail "empty_v6_not_pending"

migrate_first="$(run_flyway migrate)"
grep -Fq 'migrate.success=true' <<<"$migrate_first" || ci_fail "empty_first_migrate_failed"
grep -Fq 'migrate.migrationsExecuted=6' <<<"$migrate_first" \
    || ci_fail "empty_first_migrate_count_unexpected"

validate_output="$(run_flyway validate)"
grep -Fq 'validate.success=true' <<<"$validate_output" || ci_fail "empty_validate_failed"

info_after="$(run_flyway info)"
grep -Fq 'info.current=6' <<<"$info_after" || ci_fail "empty_info_current_after_unexpected"
grep -Fq 'info.migration=1|baseline schema|SUCCESS' <<<"$info_after" \
    || ci_fail "empty_v1_not_successful"
grep -Fq 'info.migration=2|stage1 business semantics and permissions|SUCCESS' <<<"$info_after" \
    || ci_fail "empty_v2_not_successful"
grep -Fq 'info.migration=3|stage2 ai invocation governance|SUCCESS' <<<"$info_after" \
    || ci_fail "empty_v3_not_successful"
grep -Fq 'info.migration=4|stage4 knowledge index and outbox|SUCCESS' <<<"$info_after" \
    || ci_fail "empty_v4_not_successful"
grep -Fq 'info.migration=5|stage5 permission aware rag|SUCCESS' <<<"$info_after" \
    || ci_fail "empty_v5_not_successful"
grep -Fq 'info.migration=6|stage5 qdrant numeric permission payload rebuild|SUCCESS' <<<"$info_after" \
    || ci_fail "empty_v6_not_successful"

post_verify_sql="${project_root}/sql/flyway/stage1/02_post_verify_v2.sql"
[[ -f "$post_verify_sql" ]] || ci_fail "empty_post_verify_missing"
post_verify_output="$(ci_mysql_migrator --database="${DB_NAME}" <"$post_verify_sql")" \
    || ci_fail "empty_post_verify_failed"
ci_assert_stage1_check_output "$post_verify_output" '^V2-V-' "12" "empty_post_verify"

v3_post_verify_sql="${project_root}/sql/flyway/stage2/02_post_verify_v3.sql"
[[ -f "$v3_post_verify_sql" ]] || ci_fail "empty_v3_post_verify_missing"
v3_post_verify_output="$(ci_mysql_migrator --database="${DB_NAME}" <"$v3_post_verify_sql")" \
    || ci_fail "empty_v3_post_verify_failed"
ci_assert_stage1_check_output "$v3_post_verify_output" '^V3-V-' "14" "empty_v3_post_verify"

v3_legacy_backfill_sql="${project_root}/sql/flyway/stage2/03_verify_v3_legacy_backfill.sql"
[[ -f "$v3_legacy_backfill_sql" ]] || ci_fail "empty_v3_legacy_backfill_verify_missing"
v3_legacy_backfill_output="$(ci_mysql_migrator --database="${DB_NAME}" <"$v3_legacy_backfill_sql")" \
    || ci_fail "empty_v3_legacy_backfill_verify_failed"
ci_assert_stage1_check_output "$v3_legacy_backfill_output" '^V3-L-' "5" "empty_v3_legacy_backfill"

v4_post_verify_sql="${project_root}/sql/flyway/stage4/post_verify_v4.sql"
[[ -f "$v4_post_verify_sql" ]] || ci_fail "empty_v4_post_verify_missing"
v4_post_verify_output="$(ci_mysql_migrator --database="${DB_NAME}" <"$v4_post_verify_sql")" \
    || ci_fail "empty_v4_post_verify_failed"
ci_assert_stage1_check_output "$v4_post_verify_output" '^V4-V-' "4" "empty_v4_post_verify"

v5_post_verify_sql="${project_root}/sql/flyway/stage5/post_verify_v5.sql"
[[ -f "$v5_post_verify_sql" ]] || ci_fail "empty_v5_post_verify_missing"
v5_post_verify_output="$(ci_mysql_migrator --database="${DB_NAME}" <"$v5_post_verify_sql")" \
    || ci_fail "empty_v5_post_verify_failed"
ci_assert_stage1_check_output "$v5_post_verify_output" '^V5-V-' "6" "empty_v5_post_verify"

v6_post_verify_sql="${project_root}/sql/flyway/stage5/post_verify_v6.sql"
[[ -f "$v6_post_verify_sql" ]] || ci_fail "empty_v6_post_verify_missing"
v6_post_verify_output="$(ci_mysql_migrator --database="${DB_NAME}" <"$v6_post_verify_sql")" \
    || ci_fail "empty_v6_post_verify_failed"
ci_assert_stage1_check_output "$v6_post_verify_output" '^V6-V-' "1" "empty_v6_post_verify"

migrate_second="$(run_flyway migrate)"
grep -Fq 'migrate.success=true' <<<"$migrate_second" || ci_fail "empty_second_migrate_failed"
grep -Fq 'migrate.migrationsExecuted=0' <<<"$migrate_second" \
    || ci_fail "empty_second_migrate_not_idempotent"

business_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name <> 'flyway_schema_history';")"
all_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
history_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1;")"
v2_history_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND success=1;")"
v3_history_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='3' AND success=1;")"
v4_history_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='4' AND success=1;")"
v5_history_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='5' AND success=1;")"
v6_history_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='6' AND success=1;")"
history_total="$(ci_mysql_migrator --database="${DB_NAME}" --execute='SELECT COUNT(*) FROM flyway_schema_history;')"
business_rows="$(ci_business_row_total "${DB_NAME}")"
assignee_column="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${DB_NAME}' AND table_name='task' AND column_name='assignee_id';")"
assignee_user_column="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${DB_NAME}' AND table_name='task' AND column_name='assignee_user_id';")"
assignee_index="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${DB_NAME}' AND table_name='task' AND index_name='idx_task_assignee_id';")"
assignment_log_table="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='task_assignment_log';")"
review_task_table="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='weekly_review_task';")"
status_constraint="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='${DB_NAME}' AND table_name='task' AND constraint_name='chk_task_status_range' AND constraint_type='CHECK';")"

ci_assert_equals "30" "$business_tables" "empty_business_table_count_unexpected"
ci_assert_equals "31" "$all_tables" "empty_total_table_count_unexpected"
ci_assert_equals "1" "$history_rows" "empty_v1_history_missing"
ci_assert_equals "1" "$v2_history_rows" "empty_v2_history_missing"
ci_assert_equals "1" "$v3_history_rows" "empty_v3_history_missing"
ci_assert_equals "1" "$v4_history_rows" "empty_v4_history_missing"
ci_assert_equals "1" "$v5_history_rows" "empty_v5_history_missing"
ci_assert_equals "1" "$v6_history_rows" "empty_v6_history_missing"
ci_assert_equals "6" "$history_total" "empty_history_row_count_unexpected"
ci_assert_equals "1" "$business_rows" "empty_business_rows_unexpected"
ci_assert_equals "0" "$assignee_column" "empty_legacy_assignee_column_present"
ci_assert_equals "1" "$assignee_user_column" "empty_assignee_user_column_missing"
ci_assert_equals "0" "$assignee_index" "empty_legacy_assignee_index_present"
ci_assert_equals "1" "$assignment_log_table" "empty_assignment_log_table_missing"
ci_assert_equals "1" "$review_task_table" "empty_review_task_table_missing"
ci_assert_equals "1" "$status_constraint" "empty_status_constraint_missing"
ci_assert_application_ddl_denied

ci_emit "empty.verify.success" "true"
ci_emit "empty.business_tables" "$business_tables"
ci_emit "empty.total_tables" "$all_tables"
ci_emit "empty.business_rows" "$business_rows"
ci_emit "empty.post_verify_checks" "12"
ci_emit "empty.v3_post_verify_checks" "14"
ci_emit "empty.v3_legacy_backfill_checks" "5"
ci_emit "empty.v4_post_verify_checks" "4"
ci_emit "empty.v5_post_verify_checks" "6"
ci_emit "empty.v6_post_verify_checks" "1"
ci_emit "empty.second_migrations_executed" "0"
