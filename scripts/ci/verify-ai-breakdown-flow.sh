#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_assert_ci_app_identity
ci_require_command "curl"
ci_require_command "jq"
ci_require_command "sha256sum"
ci_require_command "mktemp"
ci_require_env "CI_FULL_STACK_BASE_URL"
ci_require_env "CI_AI_FLOW_OUTPUT_DIR"
ci_require_env "RELEASE_CANDIDATE_ID"

base_url="${CI_FULL_STACK_BASE_URL%/}"
output_dir="$CI_AI_FLOW_OUTPUT_DIR"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

request_json() {
    local method="$1"
    local path="$2"
    local body="$3"
    local output="$4"
    local token="${5:-}"
    local http_code
    local -a args=(--silent --show-error --max-time 30 --request "$method")

    if [[ -n "$token" ]]; then
        args+=(--header "Authorization: Bearer $token")
    fi
    if [[ -n "$body" ]]; then
        args+=(--header "Content-Type: application/json" --data "$body")
    fi

    http_code="$(curl "${args[@]}" --output "$output" --write-out '%{http_code}' "${base_url}${path}")"
    [[ "$http_code" =~ ^2[0-9][0-9]$ ]] || ci_fail "http_request_failed_${path//\//_}_${http_code}"
}

assert_success() {
    local file="$1"
    jq -e '.code == 0' "$file" >/dev/null || ci_fail "api_business_failure"
}

assert_failure() {
    local file="$1"
    jq -e '.code != 0' "$file" >/dev/null || ci_fail "expected_api_business_failure"
}

json_body() {
    jq -cn "$@"
}

account="ci$(printf '%s' "${GITHUB_RUN_ID:-local}${GITHUB_RUN_ATTEMPT:-1}" | tr -cd '[:alnum:]')"
username="ci-user-${account}"
password="CiGate-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}-Aa9!"

request_json POST /api/user/register \
    "$(json_body --arg account "$account" --arg username "$username" --arg password "$password" \
        '{account:$account,username:$username,password:$password,confirmPassword:$password}')" \
    "$work_dir/register.json"
assert_success "$work_dir/register.json"

request_json POST /api/user/login \
    "$(json_body --arg account "$account" --arg password "$password" '{account:$account,password:$password}')" \
    "$work_dir/login.json"
assert_success "$work_dir/login.json"
token="$(jq -er '.data.token | select(type == "string" and length > 0)' "$work_dir/login.json")"
user_id="$(jq -er '.data.id | numbers' "$work_dir/login.json")"
printf '::add-mask::%s\n' "$password"
printf '::add-mask::%s\n' "$token"

request_json GET /api/user/me "" "$work_dir/me.json" "$token"
assert_success "$work_dir/me.json"
jq -e --argjson userId "$user_id" '.data.id == $userId' "$work_dir/me.json" >/dev/null \
    || ci_fail "authenticated_user_mismatch"

preview_request="$(json_body --arg target "D2-C-$RELEASE_CANDIDATE_ID" --arg duration "8周" \
    --arg description "CI deterministic AI breakdown flow" '{target:$target,duration:$duration,description:$description,detailed:false}')"

# Cancellation path: preview -> detail(PREVIEW) -> cancel -> detail(CANCELED) -> confirm rejected.
request_json POST /api/ai/breakdown/preview "$preview_request" "$work_dir/preview-cancel.json" "$token"
assert_success "$work_dir/preview-cancel.json"
cancel_draft_id="$(jq -er '.data.draftId | select(type == "string" and length > 0)' "$work_dir/preview-cancel.json")"
cancel_milestone_count="$(jq -er '.data.milestones | length' "$work_dir/preview-cancel.json")"
cancel_task_count="$(jq -er '[.data.milestones[].tasks[]] | length' "$work_dir/preview-cancel.json")"
[[ "$cancel_milestone_count" == "2" && "$cancel_task_count" == "4" ]] \
    || ci_fail "ai_stub_payload_shape_invalid"

request_json GET "/api/ai/draft/${cancel_draft_id}" "" "$work_dir/cancel-detail-before.json" "$token"
assert_success "$work_dir/cancel-detail-before.json"
jq -e '.data.status == 0' "$work_dir/cancel-detail-before.json" >/dev/null \
    || ci_fail "cancel_draft_initial_status_invalid"

request_json POST /api/ai/draft/cancel \
    "$(json_body --arg draftId "$cancel_draft_id" '{draftId:$draftId}')" \
    "$work_dir/cancel.json" "$token"
assert_success "$work_dir/cancel.json"
jq -e '.data == true' "$work_dir/cancel.json" >/dev/null || ci_fail "cancel_draft_failed"

request_json GET "/api/ai/draft/${cancel_draft_id}" "" "$work_dir/cancel-detail-after.json" "$token"
assert_success "$work_dir/cancel-detail-after.json"
jq -e '.data.status == 2' "$work_dir/cancel-detail-after.json" >/dev/null \
    || ci_fail "cancel_draft_final_status_invalid"

request_json POST /api/ai/breakdown/confirm \
    "$(json_body --arg draftId "$cancel_draft_id" --arg operationId "cancel-confirm-$RELEASE_CANDIDATE_ID" \
        '{draftId:$draftId,operationId:$operationId,projectName:"cancelled-ci-project"}')" \
    "$work_dir/cancel-confirm.json" "$token"
assert_failure "$work_dir/cancel-confirm.json"

# Confirmation path: preview -> confirm -> detail(CONFIRMED) -> same operation replay.
request_json POST /api/ai/breakdown/preview "$preview_request" "$work_dir/preview-confirm.json" "$token"
assert_success "$work_dir/preview-confirm.json"
confirm_draft_id="$(jq -er '.data.draftId | select(type == "string" and length > 0)' "$work_dir/preview-confirm.json")"
confirm_milestone_count="$(jq -er '.data.milestones | length' "$work_dir/preview-confirm.json")"
confirm_task_count="$(jq -er '[.data.milestones[].tasks[]] | length' "$work_dir/preview-confirm.json")"
# ai_draft_confirm_log.operation_id is VARCHAR(64); derive a bounded ID even
# when a caller supplies a candidate ID at the maximum accepted length.
operation_id="confirm-$(printf '%s' "$RELEASE_CANDIDATE_ID" | sha256sum | awk '{print substr($1, 1, 48)}')"
project_name="D2-C-$RELEASE_CANDIDATE_ID"

confirm_body="$(json_body --arg draftId "$confirm_draft_id" --arg operationId "$operation_id" \
    --arg projectName "$project_name" '{draftId:$draftId,operationId:$operationId,projectName:$projectName,projectGoal:"CI AI breakdown gate"}')"
request_json POST /api/ai/breakdown/confirm "$confirm_body" "$work_dir/confirm.json" "$token"
assert_success "$work_dir/confirm.json"
business_id="$(jq -er '.data.businessId | numbers' "$work_dir/confirm.json")"
jq -e '.data.success == true and .data.idempotentReplay == false' "$work_dir/confirm.json" >/dev/null \
    || ci_fail "confirm_first_result_invalid"

request_json GET "/api/ai/draft/${confirm_draft_id}" "" "$work_dir/confirm-detail.json" "$token"
assert_success "$work_dir/confirm-detail.json"
jq -e '.data.status == 1' "$work_dir/confirm-detail.json" >/dev/null \
    || ci_fail "confirm_draft_final_status_invalid"

request_json POST /api/ai/breakdown/confirm "$confirm_body" "$work_dir/replay.json" "$token"
assert_success "$work_dir/replay.json"
jq -e --argjson businessId "$business_id" \
    '.data.success == true and .data.idempotentReplay == true and .data.businessId == $businessId' \
    "$work_dir/replay.json" >/dev/null || ci_fail "confirm_idempotent_replay_invalid"

request_json GET "/api/project/list?pageNum=1&pageSize=1000&keyword=${project_name}" "" \
    "$work_dir/projects.json" "$token"
assert_success "$work_dir/projects.json"
project_count="$(jq -er --arg name "$project_name" '[.data.records[] | select(.name == $name)] | length' "$work_dir/projects.json")"
[[ "$project_count" == "1" ]] || ci_fail "confirmed_project_count_invalid"

request_json GET "/api/milestone/list?projectId=${business_id}" "" "$work_dir/milestones.json" "$token"
assert_success "$work_dir/milestones.json"
milestone_count="$(jq -er '.data | length' "$work_dir/milestones.json")"
[[ "$milestone_count" == "$confirm_milestone_count" ]] || ci_fail "confirmed_milestone_count_invalid"

request_json GET "/api/task/list?projectId=${business_id}&current=1&size=1000" "" \
    "$work_dir/tasks.json" "$token"
assert_success "$work_dir/tasks.json"
task_count="$(jq -er '.data.records | length' "$work_dir/tasks.json")"
[[ "$task_count" == "$confirm_task_count" ]] || ci_fail "confirmed_task_count_invalid"

request_json GET "/api/ai/call-log/list?scene=task-breakdown&current=1&size=100" "" \
    "$work_dir/call-logs.json" "$token"
assert_success "$work_dir/call-logs.json"
call_log_count="$(jq -er '.data.records | length' "$work_dir/call-logs.json")"
success_call_log_count="$(jq -er '[.data.records[] | select(.status == 1 and .modelName == "ci-ai-stub")] | length' "$work_dir/call-logs.json")"
[[ "$call_log_count" == "2" && "$success_call_log_count" == "2" ]] \
    || ci_fail "ai_call_log_audit_invalid"

mkdir -p "$output_dir"
evidence="$output_dir/full-stack-ai-flow-evidence.json"
jq -n \
    --arg candidate "$RELEASE_CANDIDATE_ID" \
    --argjson previewMilestones "$confirm_milestone_count" \
    --argjson previewTasks "$confirm_task_count" \
    --argjson projectCount "$project_count" \
    --argjson milestoneCount "$milestone_count" \
    --argjson taskCount "$task_count" \
    --argjson callLogCount "$success_call_log_count" \
    '{
        schemaVersion: 1,
        status: "PASS",
        candidateId: $candidate,
        frontendViaNginx: "PASS",
        apiProxy: "PASS",
        aiProvider: "deterministic-ci-stub",
        aiBreakdown: {
            preview: "PASS",
            cancel: "PASS",
            confirm: "PASS",
            idempotentReplay: "PASS",
            previewMilestoneCount: $previewMilestones,
            previewTaskCount: $previewTasks,
            confirmedProjectCount: $projectCount,
            confirmedMilestoneCount: $milestoneCount,
            confirmedTaskCount: $taskCount,
            successfulTaskBreakdownCallLogCount: $callLogCount
        }
    }' > "$evidence"

evidence_sha256="$(sha256sum "$evidence" | awk '{print toupper($1)}')"
printf '%s  %s\n' "$evidence_sha256" "$(basename -- "$evidence")" > "${evidence}.sha256"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf 'full_stack_evidence_sha256=%s\n' "$evidence_sha256" >> "$GITHUB_OUTPUT"
    printf 'full_stack_confirmed_project_count=%s\n' "$project_count" >> "$GITHUB_OUTPUT"
    printf 'full_stack_confirmed_milestone_count=%s\n' "$milestone_count" >> "$GITHUB_OUTPUT"
    printf 'full_stack_confirmed_task_count=%s\n' "$task_count" >> "$GITHUB_OUTPUT"
fi
printf 'full_stack.ai_breakdown=PASS\n'
printf 'full_stack.confirmed_project_count=%s\n' "$project_count"
printf 'full_stack.confirmed_milestone_count=%s\n' "$milestone_count"
printf 'full_stack.confirmed_task_count=%s\n' "$task_count"
printf 'full_stack.evidence_sha256=%s\n' "$evidence_sha256"
