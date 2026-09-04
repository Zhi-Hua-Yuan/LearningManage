#!/usr/bin/env bash

set -Eeuo pipefail

v3_project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
v3_legacy_fixture="${v3_project_root}/src/test/resources/db/legacy/pre_flyway_v1_schema.sql"
v3_stage1_seed="${v3_project_root}/src/test/resources/db/stage1/v1_to_v2_seed.sql"
v3_stage2_seed="${v3_project_root}/src/test/resources/db/stage2/v2_to_v3_seed.sql"
v3_v2_migration="${v3_project_root}/src/main/resources/db/migration/V2__stage1_business_semantics_and_permissions.sql"
v3_migration="${v3_project_root}/src/main/resources/db/migration/V3__stage2_ai_invocation_governance.sql"
v3_preflight="${v3_project_root}/sql/flyway/stage2/01_preflight_v3.sql"
v3_post_verify="${v3_project_root}/sql/flyway/stage2/02_post_verify_v3.sql"
v3_legacy_backfill_verify="${v3_project_root}/sql/flyway/stage2/03_verify_v3_legacy_backfill.sql"

v3_require_inputs() {
    local required_file
    for required_file in \
        "$v3_legacy_fixture" "$v3_stage1_seed" "$v3_stage2_seed" \
        "$v3_v2_migration" "$v3_migration" "$v3_preflight" "$v3_post_verify" \
        "$v3_legacy_backfill_verify"; do
        [[ -f "$required_file" ]] || ci_fail "v3_input_missing"
    done
}

v3_assert_legacy_backfill() {
    local output="$1"
    local label="$2"
    local check_count
    local failure_count

    output="${output//$'\r'/}"
    check_count="$(awk -F '\t' '$1 ~ /^V3-L-/ { count++ } END { print count + 0 }' <<<"$output")"
    failure_count="$(awk -F '\t' '$1 ~ /^V3-L-/ && ($3 != "0" || $4 != "PASS") { count++ } END { print count + 0 }' <<<"$output")"

    ci_assert_equals "5" "$check_count" "${label}_check_count_unexpected"
    ci_assert_equals "0" "$failure_count" "${label}_check_failed"
}

v3_create_database() {
    local database_name="$1"
    ci_assert_ci_database_name "$database_name"
    if ! ci_mysql_admin >/dev/null 2>&1 <<SQL
CREATE DATABASE \`${database_name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, CREATE TEMPORARY TABLES, ALTER, DROP, INDEX, REFERENCES
    ON \`${database_name}\`.* TO '${FLYWAY_DB_USERNAME}'@'%';
SQL
    then
        ci_fail "v3_database_provision_failed"
    fi
}

v3_prepare_v2_database() {
    local database_name="$1"
    v3_create_database "$database_name"
    ci_mysql_migrator --database="$database_name" <"$v3_legacy_fixture" >/dev/null \
        || ci_fail "v3_legacy_fixture_import_failed"
    ci_mysql_migrator --database="$database_name" <"$v3_stage1_seed" >/dev/null \
        || ci_fail "v3_stage1_seed_import_failed"
    ci_mysql_migrator --database="$database_name" <"$v3_v2_migration" >/dev/null \
        || ci_fail "v3_v2_schema_prepare_failed"
    ci_mysql_migrator --database="$database_name" <"$v3_stage2_seed" >/dev/null \
        || ci_fail "v3_stage2_seed_import_failed"
}

v3_assert_preflight() {
    local output="$1"
    local expected_failure_count="$2"
    local expected_repairable_groups="$3"
    local label="$4"
    local check_count
    local failure_count
    local repairable_groups

    output="${output//$'\r'/}"
    check_count="$(awk -F '\t' '$1 ~ /^V3-P-/ { count++ } END { print count + 0 }' <<<"$output")"
    failure_count="$(awk -F '\t' '$1 ~ /^V3-P-/ && $5 == "FAIL" { count++ } END { print count + 0 }' <<<"$output")"
    repairable_groups="$(awk -F '\t' '$1 == "V3-P-010" { print $3 + 0 }' <<<"$output")"

    ci_assert_equals "13" "$check_count" "${label}_check_count_unexpected"
    ci_assert_equals "$expected_failure_count" "$failure_count" "${label}_failure_count_unexpected"
    ci_assert_equals "$expected_repairable_groups" "$repairable_groups" "${label}_repairable_count_unexpected"
}

v3_assert_post_verify() {
    local output="$1"
    local label="$2"
    local check_count
    local failure_count

    output="${output//$'\r'/}"
    check_count="$(awk -F '\t' '$1 ~ /^V3-V-/ { count++ } END { print count + 0 }' <<<"$output")"
    failure_count="$(awk -F '\t' '$1 ~ /^V3-V-/ && ($3 != "0" || $4 != "PASS") { count++ } END { print count + 0 }' <<<"$output")"

    ci_assert_equals "14" "$check_count" "${label}_check_count_unexpected"
    ci_assert_equals "0" "$failure_count" "${label}_check_failed"
}
