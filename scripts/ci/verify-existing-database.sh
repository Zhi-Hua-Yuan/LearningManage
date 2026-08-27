#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
fixture="${project_root}/src/test/resources/db/legacy/pre_flyway_v1_schema.sql"
fixture_sha256="1ECF286291C3276585DA18722348BC4D70FAC8B751C0563568CC4B58B417FF96"
seed_fixture="${project_root}/src/test/resources/db/stage1/v1_to_v2_seed.sql"
seed_fixture_sha256="914E302E9E97FEBBF10885F993A1A3474FCE34F7BF9AAD7C82EA65D70F866733"
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
ci_require_command "sha256sum"
[[ "${DB_NAME}" == "${CI_LEGACY_DB_NAME:-}" ]] || ci_fail "legacy_database_name_mismatch"
[[ "${FLYWAY_BASELINE_AUTHORIZED:-false}" != "true" ]] \
    || ci_fail "baseline_authorization_must_not_be_global"
[[ -f "$fixture" ]] || ci_fail "legacy_fixture_missing"
[[ -f "$seed_fixture" ]] || ci_fail "stage1_seed_fixture_missing"

actual_fixture_sha256="$(sha256sum "$fixture" | awk '{print toupper($1)}')"
ci_assert_equals "$fixture_sha256" "$actual_fixture_sha256" "legacy_fixture_checksum_mismatch"
actual_seed_fixture_sha256="$(sha256sum "$seed_fixture" | awk '{print toupper($1)}')"
ci_assert_equals "$seed_fixture_sha256" "$actual_seed_fixture_sha256" "stage1_seed_fixture_checksum_mismatch"

initial_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
ci_assert_equals "0" "$initial_tables" "legacy_database_not_empty"

if ! ci_mysql_migrator --database="${DB_NAME}" <"$fixture" >/dev/null 2>&1; then
    ci_fail "legacy_fixture_import_failed"
fi
if ! ci_mysql_migrator --database="${DB_NAME}" <"$seed_fixture" >/dev/null 2>&1; then
    ci_fail "stage1_seed_fixture_import_failed"
fi

business_tables_before="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
history_tables_before="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='flyway_schema_history';")"
user_rows_before="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM \`user\`;")"
team_rows_before="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM team;")"
team_member_rows_before="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM team_member;")"
project_rows_before="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM project;")"
milestone_rows_before="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM milestone;")"
task_rows_before="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM task;")"
weekly_review_rows_before="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM weekly_review;")"
business_rows_before="$(ci_business_row_total "${DB_NAME}")"
ci_assert_equals "20" "$business_tables_before" "legacy_fixture_table_count_unexpected"
ci_assert_equals "0" "$history_tables_before" "legacy_history_exists_before_baseline"
ci_assert_equals "5" "$user_rows_before" "stage1_seed_user_count_unexpected"
ci_assert_equals "1" "$team_rows_before" "stage1_seed_team_count_unexpected"
ci_assert_equals "3" "$team_member_rows_before" "stage1_seed_team_member_count_unexpected"
ci_assert_equals "2" "$project_rows_before" "stage1_seed_project_count_unexpected"
ci_assert_equals "2" "$milestone_rows_before" "stage1_seed_milestone_count_unexpected"
ci_assert_equals "5" "$task_rows_before" "stage1_seed_task_count_unexpected"
ci_assert_equals "2" "$weekly_review_rows_before" "stage1_seed_weekly_review_count_unexpected"

preflight_sql="${project_root}/sql/flyway/stage1/01_preflight_v2.sql"
[[ -f "$preflight_sql" ]] || ci_fail "legacy_preflight_missing"
preflight_output="$(ci_mysql_migrator --database="${DB_NAME}" <"$preflight_sql")" \
    || ci_fail "legacy_preflight_failed"
ci_assert_stage1_check_output "$preflight_output" '^V2-P-' "25" "legacy_preflight"

info_before="$(run_flyway info)"
grep -Fq 'info.current=<none>' <<<"$info_before" || ci_fail "legacy_info_current_unexpected"
grep -Fq 'info.migration=1|baseline schema|PENDING' <<<"$info_before" \
    || ci_fail "legacy_v1_not_pending"
grep -Fq 'info.migration=2|stage1 business semantics and permissions|PENDING' <<<"$info_before" \
    || ci_fail "legacy_v2_not_pending"

if ! baseline_output="$(FLYWAY_BASELINE_AUTHORIZED=true FLYWAY_BASELINE_VERSION=1 \
    "${project_root}/scripts/flyway-admin.sh" baseline 2>&1)"; then
    ci_fail "flyway_baseline_failed"
fi
grep -Fq 'baseline.baselineVersion=1' <<<"$baseline_output" \
    || ci_fail "legacy_baseline_version_unexpected"
grep -Fq 'baseline.success=true' <<<"$baseline_output" || ci_fail "legacy_baseline_failed"

validate_output="$(run_flyway validate)"
grep -Fq 'validate.success=true' <<<"$validate_output" || ci_fail "legacy_validate_failed"

info_after="$(run_flyway info)"
grep -Fq 'info.current=1' <<<"$info_after" || ci_fail "legacy_info_current_after_unexpected"
grep -Fq 'info.migration=1|baseline schema|BASELINE_IGNORED' <<<"$info_after" \
    || ci_fail "legacy_v1_not_baseline_ignored"
grep -Fq 'info.migration=1|<< Flyway Baseline >>|BASELINE' <<<"$info_after" \
    || ci_fail "legacy_baseline_info_missing"
grep -Fq 'info.migration=2|stage1 business semantics and permissions|PENDING' <<<"$info_after" \
    || ci_fail "legacy_v2_not_pending_after_baseline"

migrate_output="$(run_flyway migrate)"
grep -Fq 'migrate.success=true' <<<"$migrate_output" || ci_fail "legacy_migrate_failed"
grep -Fq 'migrate.migrationsExecuted=1' <<<"$migrate_output" \
    || ci_fail "legacy_migrate_count_unexpected"

post_verify_sql="${project_root}/sql/flyway/stage1/02_post_verify_v2.sql"
[[ -f "$post_verify_sql" ]] || ci_fail "legacy_post_verify_missing"
post_verify_output="$(ci_mysql_migrator --database="${DB_NAME}" <"$post_verify_sql")" \
    || ci_fail "legacy_post_verify_failed"
ci_assert_stage1_check_output "$post_verify_output" '^V2-V-' "12" "legacy_post_verify"

business_tables_after="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name <> 'flyway_schema_history';")"
all_tables_after="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
history_total="$(ci_mysql_migrator --database="${DB_NAME}" --execute='SELECT COUNT(*) FROM flyway_schema_history;')"
baseline_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND type='BASELINE' AND success=1;")"
sql_v1_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND type='SQL' AND success=1;")"
sql_v2_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND type='SQL' AND success=1;")"
business_rows_after="$(ci_business_row_total "${DB_NAME}")"
task_assignment_log_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM task_assignment_log;")"
weekly_review_task_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM weekly_review_task;")"
canonical_user_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM \`user\` WHERE user_role IN ('USER', 'SYSTEM_ADMIN');")"

ci_assert_equals "22" "$business_tables_after" "legacy_business_table_count_unexpected"
ci_assert_equals "23" "$all_tables_after" "legacy_total_table_count_unexpected"
ci_assert_equals "2" "$history_total" "legacy_history_row_count_unexpected"
ci_assert_equals "1" "$baseline_rows" "legacy_baseline_history_missing"
ci_assert_equals "0" "$sql_v1_rows" "legacy_v1_sql_unexpectedly_executed"
ci_assert_equals "1" "$sql_v2_rows" "legacy_v2_sql_history_missing"
ci_assert_equals "5" "$task_assignment_log_rows" "legacy_assignment_log_count_unexpected"
ci_assert_equals "0" "$weekly_review_task_rows" "legacy_review_task_count_unexpected"
ci_assert_equals "5" "$canonical_user_rows" "legacy_canonical_user_role_count_unexpected"
ci_assert_equals "25" "$business_rows_after" "legacy_business_rows_after_unexpected"
ci_assert_equals "20" "$business_rows_before" "legacy_business_rows_before_unexpected"
ci_assert_application_ddl_denied

ci_emit "legacy.verify.success" "true"
ci_emit "legacy.business_tables" "$business_tables_after"
ci_emit "legacy.total_tables" "$all_tables_after"
ci_emit "legacy.business_rows" "$business_rows_after"
ci_emit "legacy.post_verify_checks" "12"
ci_emit "legacy.migrations_executed" "1"
