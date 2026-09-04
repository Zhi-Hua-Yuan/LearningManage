#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"
# shellcheck source=lib/v3-test-common.sh
source "${script_dir}/lib/v3-test-common.sh"

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_require_command "awk"
ci_require_command "mysql"
ci_require_env "CI_LEGACY_DB_NAME"
v3_require_inputs

declare -a negative_cases=(
    "conflicting_business|V3-P-011|1|${project_root}/src/test/resources/db/stage2/negative/conflicting_business_id.sql"
    "conflicting_scene|V3-P-011,V3-P-014|2|${project_root}/src/test/resources/db/stage2/negative/conflicting_scene.sql"
    "orphan_confirmation|V3-P-012|1|${project_root}/src/test/resources/db/stage2/negative/orphan_confirmation.sql"
    "owner_mismatch|V3-P-013|1|${project_root}/src/test/resources/db/stage2/negative/owner_mismatch.sql"
    "status_mismatch|V3-P-015|1|${project_root}/src/test/resources/db/stage2/negative/status_mismatch.sql"
    "canceled_status_mismatch|V3-P-015|1|${project_root}/src/test/resources/db/stage2/negative/canceled_status_mismatch.sql"
    "expired_status_mismatch|V3-P-015|1|${project_root}/src/test/resources/db/stage2/negative/expired_status_mismatch.sql"
    "confirmed_without_log|V3-P-016|1|${project_root}/src/test/resources/db/stage2/negative/confirmed_without_log.sql"
    "preexisting_archive|V3-P-002|1|${project_root}/src/test/resources/db/stage2/negative/preexisting_archive.sql"
    "preexisting_column|V3-P-003|1|${project_root}/src/test/resources/db/stage2/negative/preexisting_column.sql"
    "missing_legacy_index|V3-P-004|1|${project_root}/src/test/resources/db/stage2/negative/missing_legacy_index.sql"
    "preexisting_new_index|V3-P-005|1|${project_root}/src/test/resources/db/stage2/negative/preexisting_new_index.sql"
    "unknown_draft_status|V3-P-017|1|${project_root}/src/test/resources/db/stage2/negative/unknown_draft_status.sql"
)

for case_definition in "${negative_cases[@]}"; do
    IFS='|' read -r case_name expected_checks expected_failure_count fixture_path <<<"$case_definition"
    negative_db="${CI_LEGACY_DB_NAME}_v3_neg_${case_name}"
    [[ -f "$fixture_path" ]] || ci_fail "v3_negative_fixture_missing_${case_name}"
    v3_prepare_v2_database "$negative_db"
    ci_mysql_migrator --database="$negative_db" <"$fixture_path" >/dev/null \
        || ci_fail "v3_negative_fixture_import_failed_${case_name}"

    preflight_output="$(ci_mysql_migrator --database="$negative_db" <"$v3_preflight")" \
        || ci_fail "v3_negative_preflight_execution_failed_${case_name}"
    v3_assert_preflight "$preflight_output" "$expected_failure_count" "0" "v3_negative_${case_name}"

    IFS=',' read -ra expected_check_array <<<"$expected_checks"
    for expected_check in "${expected_check_array[@]}"; do
        expected_present="$(awk -F '\t' -v id="$expected_check" '$1 == id && $5 == "FAIL" { count++ } END { print count + 0 }' <<<"$preflight_output")"
        ci_assert_equals "1" "$expected_present" "v3_negative_expected_failure_missing_${case_name}"
    done

    archive_table_before="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${negative_db}' AND table_name='ai_draft_confirm_log_archive';")"
    requested_model_column_before="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${negative_db}' AND table_name='ai_call_log' AND column_name='requested_model';")"
    old_unique_before="$(ci_mysql_migrator --execute="SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${negative_db}' AND table_name='ai_draft_confirm_log' AND index_name='uk_user_draft_op';")"
    new_unique_before="$(ci_mysql_migrator --execute="SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${negative_db}' AND table_name='ai_draft_confirm_log' AND index_name='uk_ai_confirm_user_draft';")"

    if ci_mysql_migrator --database="$negative_db" <"$v3_migration" >/dev/null 2>&1; then
        ci_fail "v3_negative_migration_unexpectedly_succeeded_${case_name}"
    fi

    archive_table="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${negative_db}' AND table_name='ai_draft_confirm_log_archive';")"
    requested_model_column="$(ci_mysql_migrator --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${negative_db}' AND table_name='ai_call_log' AND column_name='requested_model';")"
    old_unique="$(ci_mysql_migrator --execute="SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${negative_db}' AND table_name='ai_draft_confirm_log' AND index_name='uk_user_draft_op';")"
    new_unique="$(ci_mysql_migrator --execute="SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${negative_db}' AND table_name='ai_draft_confirm_log' AND index_name='uk_ai_confirm_user_draft';")"
    ci_assert_equals "$archive_table_before" "$archive_table" "v3_negative_archive_changed_${case_name}"
    ci_assert_equals "$requested_model_column_before" "$requested_model_column" "v3_negative_columns_changed_${case_name}"
    ci_assert_equals "$old_unique_before" "$old_unique" "v3_negative_old_unique_changed_${case_name}"
    ci_assert_equals "$new_unique_before" "$new_unique" "v3_negative_new_unique_changed_${case_name}"

    ci_emit "v3.negative.case" "$case_name"
    ci_emit "v3.negative.expected_checks" "$expected_checks"
done

ci_emit "v3.negative.verify.success" "true"
ci_emit "v3.negative.cases" "${#negative_cases[@]}"
