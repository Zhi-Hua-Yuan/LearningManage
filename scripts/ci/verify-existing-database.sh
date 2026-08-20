#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
fixture="${project_root}/src/test/resources/db/legacy/pre_flyway_v1_schema.sql"
fixture_sha256="1ECF286291C3276585DA18722348BC4D70FAC8B751C0563568CC4B58B417FF96"
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

actual_fixture_sha256="$(sha256sum "$fixture" | awk '{print toupper($1)}')"
ci_assert_equals "$fixture_sha256" "$actual_fixture_sha256" "legacy_fixture_checksum_mismatch"

initial_tables="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
ci_assert_equals "0" "$initial_tables" "legacy_database_not_empty"

if ! ci_mysql_migrator --database="${DB_NAME}" <"$fixture" >/dev/null 2>&1; then
    ci_fail "legacy_fixture_import_failed"
fi

business_tables_before="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
history_tables_before="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='flyway_schema_history';")"
business_rows_before="$(ci_business_row_total "${DB_NAME}")"
ci_assert_equals "20" "$business_tables_before" "legacy_fixture_table_count_unexpected"
ci_assert_equals "0" "$history_tables_before" "legacy_history_exists_before_baseline"
ci_assert_equals "0" "$business_rows_before" "legacy_fixture_contains_rows"

info_before="$(run_flyway info)"
grep -Fq 'info.current=<none>' <<<"$info_before" || ci_fail "legacy_info_current_unexpected"
grep -Fq 'info.migration=1|baseline schema|PENDING' <<<"$info_before" \
    || ci_fail "legacy_v1_not_pending"

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

migrate_output="$(run_flyway migrate)"
grep -Fq 'migrate.success=true' <<<"$migrate_output" || ci_fail "legacy_migrate_failed"
grep -Fq 'migrate.migrationsExecuted=0' <<<"$migrate_output" \
    || ci_fail "legacy_migrate_count_unexpected"

business_tables_after="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name <> 'flyway_schema_history';")"
all_tables_after="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
history_total="$(ci_mysql_migrator --database="${DB_NAME}" --execute='SELECT COUNT(*) FROM flyway_schema_history;')"
baseline_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND type='BASELINE' AND success=1;")"
sql_v1_rows="$(ci_mysql_migrator --database="${DB_NAME}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND type='SQL' AND success=1;")"
business_rows_after="$(ci_business_row_total "${DB_NAME}")"

ci_assert_equals "20" "$business_tables_after" "legacy_business_table_count_changed"
ci_assert_equals "21" "$all_tables_after" "legacy_total_table_count_unexpected"
ci_assert_equals "1" "$history_total" "legacy_history_row_count_unexpected"
ci_assert_equals "1" "$baseline_rows" "legacy_baseline_history_missing"
ci_assert_equals "0" "$sql_v1_rows" "legacy_v1_sql_unexpectedly_executed"
ci_assert_equals "$business_rows_before" "$business_rows_after" "legacy_business_rows_changed"
ci_assert_application_ddl_denied

ci_emit "legacy.verify.success" "true"
ci_emit "legacy.business_tables" "$business_tables_after"
ci_emit "legacy.total_tables" "$all_tables_after"
ci_emit "legacy.business_rows" "$business_rows_after"
ci_emit "legacy.migrations_executed" "0"
