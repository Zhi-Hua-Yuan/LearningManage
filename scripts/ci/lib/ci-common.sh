#!/usr/bin/env bash

set -Eeuo pipefail

ci_emit() {
    local key="$1"
    local value="$2"
    printf '%s=%s\n' "$key" "$value"
}

ci_fail() {
    local code="$1"
    ci_emit "gate.status" "FAIL"
    ci_emit "gate.error" "$code"
    exit 1
}

ci_require_command() {
    local command_name="$1"
    command -v "$command_name" >/dev/null 2>&1 || ci_fail "missing_command_${command_name}"
}

ci_require_env() {
    local name="$1"
    [[ -n "${!name:-}" ]] || ci_fail "missing_environment_${name}"
}

ci_validate_identifier() {
    local value="$1"
    [[ "$value" =~ ^[A-Za-z0-9_]+$ ]] || ci_fail "invalid_identifier"
}

ci_validate_password_for_sql() {
    local value="$1"
    [[ "$value" =~ ^[A-Za-z0-9._-]{16,128}$ ]] || ci_fail "unsafe_ci_password_format"
}

ci_assert_ci_database_name() {
    local database_name="$1"
    ci_validate_identifier "$database_name"
    [[ ${#database_name} -le 64 ]] || ci_fail "database_name_too_long"
    [[ "$database_name" =~ ^learning_manage_ci_(empty|legacy)(_[a-z0-9][a-z0-9_]*)?$ ]] \
        || ci_fail "database_name_not_allowed"
}

ci_assert_ci_connection() {
    ci_require_env "CI_DB_GATE_AUTHORIZED"
    [[ "${CI_DB_GATE_AUTHORIZED}" == "true" ]] || ci_fail "ci_database_gate_not_authorized"

    ci_require_env "DB_HOST"
    [[ "${DB_HOST}" == "127.0.0.1" ]] || ci_fail "database_host_not_allowed"

    ci_require_env "DB_PORT"
    [[ "${DB_PORT}" =~ ^[1-9][0-9]{0,4}$ ]] || ci_fail "database_port_invalid"
    (( DB_PORT >= 1 && DB_PORT <= 65535 )) || ci_fail "database_port_invalid"
    [[ "${DB_PORT}" != "3306" ]] || ci_fail "database_port_3306_forbidden"
}

ci_assert_ci_target() {
    ci_assert_ci_connection
    ci_require_env "DB_NAME"
    ci_assert_ci_database_name "${DB_NAME}"
    ci_require_env "FLYWAY_EXPECTED_DB_NAME"
    [[ "${FLYWAY_EXPECTED_DB_NAME}" == "${DB_NAME}" ]] || ci_fail "expected_database_mismatch"
}

ci_assert_ci_flyway_identity() {
    ci_require_env "FLYWAY_DB_USERNAME"
    ci_require_env "FLYWAY_DB_PASSWORD"
    [[ "${FLYWAY_DB_USERNAME}" == "learning_manage_ci_migrator" ]] \
        || ci_fail "flyway_username_not_allowed"
}

ci_assert_ci_app_identity() {
    ci_require_env "TEST_DB_USERNAME"
    ci_require_env "TEST_DB_PASSWORD"
    [[ "${TEST_DB_USERNAME}" == "learning_manage_ci_app" ]] \
        || ci_fail "application_username_not_allowed"
}

ci_mysql_admin() {
    ci_require_env "CI_MYSQL_ADMIN_USERNAME"
    ci_require_env "CI_MYSQL_ADMIN_PASSWORD"
    MYSQL_PWD="${CI_MYSQL_ADMIN_PASSWORD}" mysql \
        --protocol=TCP \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${CI_MYSQL_ADMIN_USERNAME}" \
        --batch \
        --skip-column-names \
        "$@"
}

ci_mysql_migrator() {
    ci_assert_ci_flyway_identity
    MYSQL_PWD="${FLYWAY_DB_PASSWORD}" mysql \
        --protocol=TCP \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${FLYWAY_DB_USERNAME}" \
        --batch \
        --skip-column-names \
        "$@"
}

ci_mysql_app() {
    ci_assert_ci_app_identity
    MYSQL_PWD="${TEST_DB_PASSWORD}" mysql \
        --protocol=TCP \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${TEST_DB_USERNAME}" \
        --batch \
        --skip-column-names \
        "$@"
}

ci_assert_equals() {
    local expected="$1"
    local actual="$2"
    local code="$3"
    [[ "$actual" == "$expected" ]] || ci_fail "$code"
}

ci_assert_stage1_check_output() {
    local output="$1"
    local prefix="$2"
    local expected_count="$3"
    local label="$4"
    local check_count
    local failure_count

    output="${output//$'\r'/}"

    check_count="$(awk -F '\t' -v prefix="$prefix" '$1 ~ prefix { count++ } END { print count + 0 }' <<<"$output")"
    failure_count="$(awk -F '\t' -v prefix="$prefix" '$1 ~ prefix && ($3 != "0" || $4 != "PASS") { count++ } END { print count + 0 }' <<<"$output")"

    ci_assert_equals "$expected_count" "$check_count" "${label}_check_count_unexpected"
    ci_assert_equals "0" "$failure_count" "${label}_check_failed"
}

ci_business_row_total() {
    local database_name="$1"
    local table_name
    local row_count
    local total=0

    while IFS= read -r table_name; do
        [[ -n "$table_name" ]] || continue
        ci_validate_identifier "$table_name"
        row_count="$(ci_mysql_migrator --database="$database_name" \
            --execute="SELECT COUNT(*) FROM \`${table_name}\`;")"
        [[ "$row_count" =~ ^[0-9]+$ ]] || ci_fail "business_row_count_invalid"
        total=$((total + row_count))
    done < <(ci_mysql_migrator --execute="SELECT table_name FROM information_schema.tables WHERE table_schema='${database_name}' AND table_name <> 'flyway_schema_history' ORDER BY table_name;" | tr -d '\r')

    printf '%s\n' "$total"
}

ci_assert_application_ddl_denied() {
    ci_mysql_app --database="${DB_NAME}" --execute='SELECT 1;' >/dev/null \
        || ci_fail "application_account_connection_failed"

    if ci_mysql_app --database="${DB_NAME}" \
        --execute='CREATE TABLE `ci_ddl_probe` (`id` bigint NOT NULL PRIMARY KEY);' >/dev/null 2>&1; then
        ci_fail "application_account_ddl_unexpectedly_allowed"
    fi
}
