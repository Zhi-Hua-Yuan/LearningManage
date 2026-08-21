#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ci_dir="$(cd -- "${script_dir}/.." && pwd)"
project_root="$(cd -- "${ci_dir}/../.." && pwd)"
guard="${ci_dir}/assert-ci-database-target.sh"
flyway_admin="${project_root}/scripts/flyway-admin.sh"
published_guard="${ci_dir}/verify-published-migrations.sh"
dockerignore="${project_root}/.dockerignore"
dockerfile="${project_root}/Dockerfile"
ci_compose="${project_root}/deploy/docker-compose.ci.yml"

passed=0

cd -- "$project_root"

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

check_maven_wrapper_executable() {
    local mode
    mode="$(git ls-files --stage -- mvnw | awk 'NR == 1 { print $1 }')"
    [[ "$mode" == "100755" ]]
}

check_dockerignore_contract() {
    [[ -f "$dockerignore" ]] || return 1
    local actual expected
    expected=$'**\n!Dockerfile\n!target/\n!target/LearningManage-0.0.1-SNAPSHOT.jar'
    actual="$(sed '/^[[:space:]]*#/d;/^[[:space:]]*$/d' "$dockerignore")"
    [[ "$actual" == "$expected" ]]
}

check_dockerfile_contract() {
    [[ -f "$dockerfile" ]] || return 1
    grep -Fqx 'ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-alpine' "$dockerfile" \
        && grep -Fqx 'FROM ${RUNTIME_IMAGE}' "$dockerfile" \
        && grep -Fqx 'COPY --chown=app:app target/LearningManage-0.0.1-SNAPSHOT.jar /app/app.jar' "$dockerfile" \
        && grep -Fqx 'USER app' "$dockerfile" \
        && grep -Fqx 'ENTRYPOINT ["java", "-jar", "/app/app.jar"]' "$dockerfile" \
        && ! grep -Fq 'target/*.jar' "$dockerfile" \
        && ! grep -Fq -- '--spring.profiles.active=prod' "$dockerfile" \
        && ! grep -Fq 'docker.1panel.live' "$dockerfile" \
        && ! grep -Eiq 'password|secret|token|api[-_]?key' "$dockerfile"
}

check_ci_compose_contract() {
    [[ -f "$ci_compose" ]] || return 1
    grep -Fq '127.0.0.1:13306:3306' "$ci_compose" \
        && grep -Fq '127.0.0.1:18123:8123' "$ci_compose" \
        && grep -Fq 'FLYWAY_ENABLED: "false"' "$ci_compose" \
        && grep -Fq 'CI_MYSQL_IMAGE' "$ci_compose" \
        && grep -Fq 'CI_BACKEND_IMAGE' "$ci_compose" \
        && grep -Fq 'CI_EMPTY_DB_NAME' "$ci_compose" \
        && ! grep -Fq 'learning_manage_app' "$ci_compose" \
        && ! grep -Fq 'learning_manage_migrator' "$ci_compose" \
        && ! grep -Fq 'mysql-data' "$ci_compose" \
        && ! grep -Fq 'frontend:' "$ci_compose" \
        && ! grep -Fq '"3306:3306"' "$ci_compose"
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
expect_pass maven_wrapper_executable check_maven_wrapper_executable
expect_pass maven_wrapper_line_ending grep -Fx '/mvnw text eol=lf' .gitattributes
expect_pass dockerignore_allowlist check_dockerignore_contract
expect_pass dockerfile_contract check_dockerfile_contract
expect_pass ci_compose_contract check_ci_compose_contract

printf 'selftest.success=true\n'
printf 'selftest.cases=%s\n' "$passed"
