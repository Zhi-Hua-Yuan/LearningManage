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
release_compose="${project_root}/deploy/docker-compose.release-gate.yml"
release_common="${ci_dir}/lib/release-candidate-common.sh"
release_workflow="${project_root}/.github/workflows/release-gate.yml"
release_schema="${project_root}/docs/stage0/ci/release-candidate-manifest.schema.json"
runtime_contract="${ci_dir}/verify-runtime-api-contract.sh"
ai_flow="${ci_dir}/verify-ai-breakdown-flow.sh"
ai_stub="${ci_dir}/stubs/ai-chat-completions-stub.py"

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
    if ("$@" >/dev/null 2>&1); then
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

check_release_workflow_contract() {
    [[ -f "$release_workflow" ]] || return 1
    grep -Fqx 'name: Cross-repository release gate' "$release_workflow" \
        && grep -Fq 'workflow_dispatch:' "$release_workflow" \
        && grep -Fq 'permissions:' "$release_workflow" \
        && grep -Fq 'contents: read' "$release_workflow" \
        && grep -Fq 'cancel-in-progress: false' "$release_workflow" \
        && grep -Fq 'repository: Zhi-Hua-Yuan/learning-manage-frontend' "$release_workflow" \
        && grep -Fq 'persist-credentials: false' "$release_workflow" \
        && [[ "$(grep -Fxc '          fetch-depth: 1' "$release_workflow")" -eq 2 ]] \
        && grep -Fq 'Confirm both protected branches stayed unchanged' "$release_workflow" \
        && grep -Fq 'FLYWAY_BASELINE_ON_MIGRATE: '\''false'\''' "$release_workflow" \
        && ! grep -Eq '^[[:space:]]+(pull_request|push|schedule):' "$release_workflow" \
        && ! grep -Fq 'pull_request_'"target" "$release_workflow" \
        && ! grep -Fq 'learning_manage_app' "$release_workflow" \
        && ! grep -Fq 'learning_manage_migrator' "$release_workflow" \
        && ! grep -Fq 'DB_PORT: '\''3306'\''' "$release_workflow"
}

check_release_manifest_schema() {
    [[ -f "$release_schema" ]] || return 1
    grep -Fq '"schemaVersion"' "$release_schema" \
        && grep -Fq '"candidateId"' "$release_schema" \
        && grep -Fq '"backend"' "$release_schema" \
        && grep -Fq '"frontend"' "$release_schema" \
        && grep -Fq '"flyway"' "$release_schema" \
        && grep -Fq '"workflow"' "$release_schema" \
        && ! grep -Eiq 'password|token|api[-_]?key|secret' "$release_schema"
}

check_frontend_contract_workflow() {
    [[ -f "$release_workflow" ]] || return 1
    grep -Fq 'npm run contract:test && npm run contract:verify' "$release_workflow" \
        && grep -Fq 'npm run contract:export' "$release_workflow" \
        && grep -Fq 'contracts/frontend-api-contract.schema.json' "$release_workflow" \
        && grep -Fq 'frontend-api-contract.sha256' "$release_workflow" \
        && grep -Fq 'frontend_contract_sha256' "$release_workflow" \
        && grep -Fq 'frontend_operation_count' "$release_workflow" \
        && grep -Fq 'frontend_contract_schema_version' "$release_workflow"
}

check_runtime_api_contract_workflow() {
    [[ -f "$runtime_contract" ]] || return 1
    grep -Fq 'verify-runtime-api-contract.sh' "$release_workflow" \
        && grep -Fq 'CI_RUNTIME_OPENAPI_URL' "$release_workflow" \
        && grep -Fq 'release-api-contract-' "$release_workflow" \
        && grep -Fq 'schemaVersion == 3' "$release_workflow" \
        && grep -Fq 'interfaceContract' "$release_schema" \
        && grep -Fq 'schemaVersion: 3' "$project_root/scripts/ci/create-release-manifest.sh" \
        && grep -Fq 'frontend_operation_missing_from_runtime_openapi' "$runtime_contract" \
        && ! grep -Eiq 'password|token|api[-_]?key|secret' "$runtime_contract"
}

check_full_stack_ai_contract() {
    [[ -f "$release_compose" && -f "$ai_flow" && -f "$ai_stub" ]] \
        && grep -Fq '127.0.0.1:18080:80' "$release_compose" \
        && grep -Fq '127.0.0.1:13306:3306' "$release_compose" \
        && grep -Fq 'AI_BASE_URL: http://ai-stub:8080/compatible-mode/v1' "$release_compose" \
        && grep -Fq 'FLYWAY_ENABLED: "false"' "$release_compose" \
        && grep -Fq 'CI_FRONTEND_DIST_DIR' "$release_compose" \
        && grep -Fq 'internal: true' "$release_compose" \
        && grep -Fq 'verify-ai-breakdown-flow.sh' "$release_workflow" \
        && grep -Fq 'release-full-stack-' "$release_workflow" \
        && grep -Fq 'fullStackRuntime' "$release_schema" \
        && grep -Fq 'deterministic-ci-stub' "$release_schema" \
        && grep -Fq 'full-stack-ai-flow-evidence.json' "$ai_flow" \
        && ! grep -Eiq 'dashscope|password|authorization|bearer' "$ai_stub"
}

# shellcheck source=scripts/ci/lib/release-candidate-common.sh
source "$release_common"

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
expect_pass release_workflow_contract check_release_workflow_contract
expect_pass release_manifest_schema check_release_manifest_schema
expect_pass frontend_contract_workflow check_frontend_contract_workflow
expect_pass runtime_api_contract_workflow check_runtime_api_contract_workflow
expect_pass full_stack_ai_contract check_full_stack_ai_contract
expect_pass release_valid_sha release_validate_sha 0123456789abcdef0123456789abcdef01234567
expect_fail release_short_sha release_validate_sha 0123456789abcdef
expect_fail release_branch_name release_validate_sha develop
expect_pass release_valid_candidate_id release_validate_candidate_id stage0-20260821-001
expect_fail release_path_traversal release_validate_candidate_id '../stage0'
expect_fail release_candidate_id_too_long release_validate_candidate_id \
    'stage0-20260821-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-extra'
expect_pass release_valid_reason release_validate_reason 'PR6-D1 candidate validation'
expect_fail release_multiline_reason release_validate_reason $'line1\nline2'

printf 'selftest.success=true\n'
printf 'selftest.cases=%s\n' "$passed"
