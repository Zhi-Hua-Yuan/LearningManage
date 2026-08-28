#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
legacy_fixture="${project_root}/src/test/resources/db/legacy/pre_flyway_v1_schema.sql"
seed_fixture="${project_root}/src/test/resources/db/stage1/v1_to_v2_seed.sql"
preflight_sql="${project_root}/sql/flyway/stage1/01_preflight_v2.sql"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_assert_ci_app_identity
ci_require_command "awk"
ci_require_command "mysql"
ci_require_command "sha256sum"
ci_require_env "CI_LEGACY_DB_NAME"
ci_require_env "CI_MYSQL_ADMIN_USERNAME"
ci_require_env "CI_MYSQL_ADMIN_PASSWORD"
ci_require_env "FLYWAY_DB_USERNAME"
[[ -f "$legacy_fixture" ]] || ci_fail "negative_legacy_fixture_missing"
[[ -f "$seed_fixture" ]] || ci_fail "negative_seed_fixture_missing"
[[ -f "$preflight_sql" ]] || ci_fail "negative_preflight_sql_missing"

declare -a negative_cases=(
    "unknown_role|V2-P-010|${project_root}/src/test/resources/db/stage1/negative/unknown_system_role.sql"
    "orphan_assignee|V2-P-021|${project_root}/src/test/resources/db/stage1/negative/orphan_assignee.sql"
    "team_assignee_not_member|V2-P-032|${project_root}/src/test/resources/db/stage1/negative/team_assignee_not_member.sql"
)

for case_definition in "${negative_cases[@]}"; do
    IFS='|' read -r case_name expected_check fixture_path <<<"$case_definition"
    negative_db="${CI_LEGACY_DB_NAME}_neg_${case_name}"
    ci_assert_ci_database_name "$negative_db"
    [[ -f "$fixture_path" ]] || ci_fail "negative_fixture_missing_${case_name}"

    if ! ci_mysql_admin >/dev/null 2>&1 <<SQL
CREATE DATABASE \`${negative_db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON \`${negative_db}\`.* TO '${FLYWAY_DB_USERNAME}'@'%';
SQL
    then
        ci_fail "negative_database_provision_failed_${case_name}"
    fi

    ci_mysql_migrator --database="$negative_db" <"$legacy_fixture" >/dev/null \
        || ci_fail "negative_legacy_fixture_import_failed_${case_name}"
    ci_mysql_migrator --database="$negative_db" <"$seed_fixture" >/dev/null \
        || ci_fail "negative_seed_fixture_import_failed_${case_name}"
    ci_mysql_migrator --database="$negative_db" <"$fixture_path" >/dev/null \
        || ci_fail "negative_fixture_import_failed_${case_name}"

    preflight_output="$(ci_mysql_migrator --database="$negative_db" <"$preflight_sql")" \
        || ci_fail "negative_preflight_execution_failed_${case_name}"
    check_count="$(awk -F '\t' '$1 ~ /^V2-P-/ { count++ } END { print count + 0 }' <<<"$preflight_output")"
    failure_count="$(awk -F '\t' '$1 ~ /^V2-P-/ && ($3 != "0" || $4 != "PASS") { count++ } END { print count + 0 }' <<<"$preflight_output")"
    expected_failure_count="$(awk -F '\t' -v expected="$expected_check" '$1 == expected && $3 != "0" && $4 == "FAIL" { count++ } END { print count + 0 }' <<<"$preflight_output")"
    ci_assert_equals "25" "$check_count" "negative_check_count_unexpected_${case_name}"
    ci_assert_equals "1" "$failure_count" "negative_failure_count_unexpected_${case_name}"
    ci_assert_equals "1" "$expected_failure_count" "negative_expected_failure_missing_${case_name}"

    assignee_user_column="$(ci_mysql_migrator --database="$negative_db" --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${negative_db}' AND table_name='task' AND column_name='assignee_user_id';")"
    relation_tables="$(ci_mysql_migrator --database="$negative_db" --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${negative_db}' AND table_name IN ('task_assignment_log', 'weekly_review_task');")"
    history_table="$(ci_mysql_migrator --database="$negative_db" --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${negative_db}' AND table_name='flyway_schema_history';")"
    ci_assert_equals "0" "$assignee_user_column" "negative_migration_started_${case_name}"
    ci_assert_equals "0" "$relation_tables" "negative_relation_tables_created_${case_name}"
    ci_assert_equals "0" "$history_table" "negative_history_table_created_${case_name}"

    ci_emit "negative.case" "$case_name"
    ci_emit "negative.expected_failure" "$expected_check"
    ci_emit "negative.failure_count" "$failure_count"
done

ci_emit "negative.preflight.verify.success" "true"
ci_emit "negative.preflight.cases" "3"
ci_emit "negative.preflight.failures" "3"
