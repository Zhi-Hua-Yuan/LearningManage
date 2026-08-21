#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"

# shellcheck source=scripts/ci/lib/release-candidate-common.sh
source "${script_dir}/lib/release-candidate-common.sh"

backend_repository="Zhi-Hua-Yuan/LearningManage"
frontend_repository="Zhi-Hua-Yuan/learning-manage-frontend"
phase="${RELEASE_CANDIDATE_PHASE:-start}"

for name in RELEASE_CANDIDATE_ID RELEASE_REASON BACKEND_SHA FRONTEND_SHA; do
    release_require_env "$name"
done

release_validate_candidate_id "$RELEASE_CANDIDATE_ID"
release_validate_reason "$RELEASE_REASON"
release_validate_sha "$BACKEND_SHA"
release_validate_sha "$FRONTEND_SHA"

case "$phase" in
    start)
        release_require_env "BACKEND_CHECKOUT_DIR"
        release_require_env "FRONTEND_CHECKOUT_DIR"

        backend_develop_sha="$(release_assert_checkout \
            "$BACKEND_CHECKOUT_DIR" "$BACKEND_SHA" "$backend_repository")"
        frontend_develop_sha="$(release_assert_checkout \
            "$FRONTEND_CHECKOUT_DIR" "$FRONTEND_SHA" "$frontend_repository")"

        published_manifest="${BACKEND_CHECKOUT_DIR}/src/test/resources/flyway/published-migrations.sha256"
        backend_ruleset="${BACKEND_CHECKOUT_DIR}/docs/stage0/ci/rulesets/protect-develop-v1.json"
        frontend_ruleset="${FRONTEND_CHECKOUT_DIR}/docs/stage0/ci/rulesets/protect-develop-v1.json"

        [[ -f "$published_manifest" ]] || release_fail "published_migration_manifest_missing"
        v1_sha256="$(awk '$2 == "src/main/resources/db/migration/V1__baseline_schema.sql" { print toupper($1) }' \
            "$published_manifest")"
        release_validate_sha256 "$v1_sha256"

        actual_v1_sha256="$(release_file_sha256 \
            "${BACKEND_CHECKOUT_DIR}/src/main/resources/db/migration/V1__baseline_schema.sql")"
        [[ "$actual_v1_sha256" == "$v1_sha256" ]] || release_fail "published_v1_checksum_mismatch"

        release_emit_output "candidate_id" "$RELEASE_CANDIDATE_ID"
        release_emit_output "backend_sha" "$BACKEND_SHA"
        release_emit_output "frontend_sha" "$FRONTEND_SHA"
        release_emit_output "backend_develop_start" "$backend_develop_sha"
        release_emit_output "frontend_develop_start" "$frontend_develop_sha"
        release_emit_output "v1_sha256" "$v1_sha256"
        release_emit_output "backend_ruleset_sha256" "$(release_file_sha256 "$backend_ruleset")"
        release_emit_output "frontend_ruleset_sha256" "$(release_file_sha256 "$frontend_ruleset")"
        ;;
    end)
        release_require_env "BACKEND_DEVELOP_START"
        release_require_env "FRONTEND_DEVELOP_START"
        release_validate_sha "$BACKEND_DEVELOP_START"
        release_validate_sha "$FRONTEND_DEVELOP_START"

        backend_develop_end="$(release_remote_develop_sha "$backend_repository")"
        frontend_develop_end="$(release_remote_develop_sha "$frontend_repository")"
        release_validate_sha "$backend_develop_end"
        release_validate_sha "$frontend_develop_end"

        [[ "$backend_develop_end" == "$BACKEND_DEVELOP_START" ]] \
            || release_fail "backend_candidate_stale"
        [[ "$frontend_develop_end" == "$FRONTEND_DEVELOP_START" ]] \
            || release_fail "frontend_candidate_stale"

        release_emit_output "backend_develop_end" "$backend_develop_end"
        release_emit_output "frontend_develop_end" "$frontend_develop_end"
        ;;
    *)
        release_fail "invalid_validation_phase"
        ;;
esac

printf 'release.status=PASS\n'
