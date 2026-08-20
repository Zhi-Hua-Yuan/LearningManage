#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ci_dir="$(cd -- "${script_dir}/.." && pwd)"
project_root="$(cd -- "${ci_dir}/../.." && pwd)"
guard="${ci_dir}/assert-ci-database-target.sh"
flyway_admin="${project_root}/scripts/flyway-admin.sh"
published_guard="${ci_dir}/verify-published-migrations.sh"

passed=0

expect_pass() {
    local name="$1"
    shift
    if ! "$@" >/dev/null 2>&1; then
        printf 'selftest.error=%s_expected_pass\n' "$name" >&2
        exit 1
    fi
    passed=$((passed + 1))
}

expect_fail() {
    local name="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        printf 'selftest.error=%s_expected_failure\n' "$name" >&2
        exit 1
    fi
    passed=$((passed + 1))
}

valid_environment=(
    env -i
    "PATH=${PATH}"
    CI_DB_GATE_AUTHORIZED=true
    DB_HOST=127.0.0.1
    DB_PORT=3311
    DB_NAME=learning_manage_ci_empty
    FLYWAY_EXPECTED_DB_NAME=learning_manage_ci_empty
    FLYWAY_DB_USERNAME=learning_manage_ci_migrator
    FLYWAY_DB_PASSWORD=ci-only-password
)

expect_pass valid_target "${valid_environment[@]}" "$guard"
expect_fail missing_authorization env -i "PATH=${PATH}" "$guard"
expect_fail main_database "${valid_environment[@]}" DB_NAME=learning_manage FLYWAY_EXPECTED_DB_NAME=learning_manage "$guard"
expect_fail port_3306 "${valid_environment[@]}" DB_PORT=3306 "$guard"
expect_fail localhost_host "${valid_environment[@]}" DB_HOST=localhost "$guard"
expect_fail external_host "${valid_environment[@]}" DB_HOST=192.0.2.10 "$guard"
expect_fail invalid_database_name "${valid_environment[@]}" DB_NAME='learning_manage_ci_empty;DROP' "$guard"
expect_fail expected_name_mismatch "${valid_environment[@]}" FLYWAY_EXPECTED_DB_NAME=learning_manage_ci_legacy "$guard"
expect_fail production_migrator "${valid_environment[@]}" FLYWAY_DB_USERNAME=learning_manage_migrator "$guard"
expect_fail flyway_clean "$flyway_admin" clean
expect_fail flyway_repair "$flyway_admin" repair
expect_fail flyway_missing_environment env -i "PATH=${PATH}" "$flyway_admin" info
expect_pass published_head env BASE_REF=HEAD "$published_guard"
expect_fail published_invalid_base env BASE_REF=refs/heads/does-not-exist "$published_guard"

printf 'selftest.success=true\n'
printf 'selftest.cases=%s\n' "$passed"
