#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_assert_ci_app_identity
ci_require_command "mysql"
ci_require_env "CI_EMPTY_DB_NAME"
ci_require_env "CI_LEGACY_DB_NAME"
ci_require_env "CI_MYSQL_ADMIN_USERNAME"
ci_require_env "CI_MYSQL_ADMIN_PASSWORD"

ci_assert_ci_database_name "${CI_EMPTY_DB_NAME}"
ci_assert_ci_database_name "${CI_LEGACY_DB_NAME}"
[[ "${CI_EMPTY_DB_NAME}" != "${CI_LEGACY_DB_NAME}" ]] || ci_fail "ci_database_names_must_differ"
ci_validate_password_for_sql "${FLYWAY_DB_PASSWORD}"
ci_validate_password_for_sql "${TEST_DB_PASSWORD}"

if ! ci_mysql_admin >/dev/null 2>&1 <<SQL
CREATE DATABASE \`${CI_EMPTY_DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE \`${CI_LEGACY_DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER '${FLYWAY_DB_USERNAME}'@'%' IDENTIFIED BY '${FLYWAY_DB_PASSWORD}';
CREATE USER '${TEST_DB_USERNAME}'@'%' IDENTIFIED BY '${TEST_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON \`${CI_EMPTY_DB_NAME}\`.* TO '${FLYWAY_DB_USERNAME}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON \`${CI_LEGACY_DB_NAME}\`.* TO '${FLYWAY_DB_USERNAME}'@'%';
GRANT SELECT, INSERT, UPDATE ON \`${CI_EMPTY_DB_NAME}\`.* TO '${TEST_DB_USERNAME}'@'%';
GRANT SELECT, INSERT, UPDATE ON \`${CI_LEGACY_DB_NAME}\`.* TO '${TEST_DB_USERNAME}'@'%';
SQL
then
    ci_fail "ci_database_provision_failed"
fi

empty_exists="$(ci_mysql_admin --execute="SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${CI_EMPTY_DB_NAME}';")"
legacy_exists="$(ci_mysql_admin --execute="SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${CI_LEGACY_DB_NAME}';")"
ci_assert_equals "1" "$empty_exists" "ci_empty_database_missing"
ci_assert_equals "1" "$legacy_exists" "ci_legacy_database_missing"

ci_emit "provision.success" "true"
ci_emit "provision.empty_database" "${CI_EMPTY_DB_NAME}"
ci_emit "provision.legacy_database" "${CI_LEGACY_DB_NAME}"
