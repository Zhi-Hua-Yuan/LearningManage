#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
fixture="${project_root}/src/test/resources/db/stage2/equivalent_duplicates.sql"
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
[[ -f "$fixture" ]] || ci_fail "v3_equivalent_fixture_missing"

database_name="${CI_LEGACY_DB_NAME}_v3_equivalent"
v3_prepare_v2_database "$database_name"
ci_mysql_migrator --database="$database_name" <"$fixture" >/dev/null \
    || ci_fail "v3_equivalent_fixture_import_failed"

preflight_output="$(ci_mysql_migrator --database="$database_name" <"$v3_preflight")" \
    || ci_fail "v3_equivalent_preflight_failed"
v3_assert_preflight "$preflight_output" "0" "2" "v3_equivalent_preflight"

ci_mysql_migrator --database="$database_name" <"$v3_migration" >/dev/null \
    || ci_fail "v3_equivalent_migration_failed"

post_verify_output="$(ci_mysql_migrator --database="$database_name" <"$v3_post_verify")" \
    || ci_fail "v3_equivalent_post_verify_failed"
v3_assert_post_verify "$post_verify_output" "v3_equivalent_post_verify"

legacy_backfill_output="$(ci_mysql_migrator --database="$database_name" <"$v3_legacy_backfill_verify")" \
    || ci_fail "v3_equivalent_legacy_backfill_verify_failed"
v3_assert_legacy_backfill "$legacy_backfill_output" "v3_equivalent_legacy_backfill"

archive_rows="$(ci_mysql_migrator --database="$database_name" --execute="SELECT COUNT(*) FROM ai_draft_confirm_log_archive;")"
live_rows="$(ci_mysql_migrator --database="$database_name" --execute="SELECT COUNT(*) FROM ai_draft_confirm_log;")"
ci_assert_equals "2" "$archive_rows" "v3_equivalent_archive_count_unexpected"
ci_assert_equals "2" "$live_rows" "v3_equivalent_live_count_unexpected"

if ci_mysql_migrator --database="$database_name" --execute="INSERT INTO ai_draft_confirm_log (id,user_id,draft_id,operation_id,scene,business_id,create_time) VALUES (8498,1101,'stage2-v3-confirmed','stage2-op-original','task-breakdown',4101,'2026-09-04 09:09:00');" >/dev/null 2>&1; then
    ci_fail "v3_same_operation_unique_constraint_not_enforced"
fi

if ci_mysql_migrator --database="$database_name" --execute="INSERT INTO ai_draft_confirm_log (id,user_id,draft_id,operation_id,scene,business_id,create_time) VALUES (8499,1101,'stage2-v3-confirmed','stage2-op-after-v3','task-breakdown',4101,'2026-09-04 09:10:00');" >/dev/null 2>&1; then
    ci_fail "v3_different_operation_unique_constraint_not_enforced"
fi

ci_mysql_migrator --database="$database_name" --execute="INSERT INTO ai_draft (id,draft_id,user_id,scene,payload_json,input_hash,status,expire_at,confirmed_at,canceled_at,create_time,update_time) VALUES (8399,'stage2-v3-second-user',1102,'task-breakdown','{\"fixture\":true}',NULL,1,'2026-09-04 09:30:00','2026-09-04 09:11:00',NULL,'2026-09-04 09:10:00','2026-09-04 09:11:00'); INSERT INTO ai_draft_confirm_log (id,user_id,draft_id,operation_id,scene,business_id,trace_id,create_time) VALUES (8500,1102,'stage2-v3-second-user','stage2-op-second-user','task-breakdown',4102,NULL,'2026-09-04 09:11:00');" >/dev/null \
    || ci_fail "v3_distinct_user_draft_insert_rejected"

distinct_pair_rows="$(ci_mysql_migrator --database="$database_name" --execute="SELECT COUNT(*) FROM ai_draft_confirm_log WHERE user_id=1102 AND draft_id='stage2-v3-second-user';")"
ci_assert_equals "1" "$distinct_pair_rows" "v3_distinct_user_draft_row_missing"

post_insert_verify_output="$(ci_mysql_migrator --database="$database_name" <"$v3_post_verify")" \
    || ci_fail "v3_equivalent_post_insert_verify_failed"
v3_assert_post_verify "$post_insert_verify_output" "v3_equivalent_post_insert_verify"

ci_emit "v3.equivalent.verify.success" "true"
ci_emit "v3.equivalent.archive_rows" "$archive_rows"
ci_emit "v3.equivalent.live_rows" "$live_rows"
ci_emit "v3.equivalent.distinct_pair_rows" "$distinct_pair_rows"
