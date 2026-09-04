#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib/ci-common.sh"

ci_require_command curl
ci_require_command jq

python_bin="${PYTHON_BIN:-python3}"
if ! command -v "$python_bin" >/dev/null 2>&1; then
  python_bin="python"
fi
command -v "$python_bin" >/dev/null 2>&1 || ci_fail "python_stub_unavailable"

stub_port="${WP2_STUB_PORT:-18081}"
[[ "$stub_port" =~ ^[1-9][0-9]{0,4}$ ]] || ci_fail "wp2_stub_port_invalid"
(( stub_port >= 1024 && stub_port <= 65535 )) || ci_fail "wp2_stub_port_invalid"

stub_log="$(mktemp)"
AI_STUB_HOST=127.0.0.1 AI_STUB_PORT="$stub_port" \
  "$python_bin" "${script_dir}/stubs/ai-chat-completions-stub.py" >"$stub_log" 2>&1 &
stub_pid=$!
cleanup() {
  kill "$stub_pid" >/dev/null 2>&1 || true
  wait "$stub_pid" >/dev/null 2>&1 || true
  rm -f "$stub_log"
}
trap cleanup EXIT

base_url="http://127.0.0.1:${stub_port}"
ready=false
for _ in $(seq 1 30); do
  if curl --silent --fail --max-time 1 "${base_url}/health" | jq -e '.status == "ok"' >/dev/null; then
    ready=true
    break
  fi
  sleep 0.2
done
[[ "$ready" == "true" ]] || ci_fail "wp2_stub_not_ready"

post_model() {
  local model="$1"
  local messages="${2:-}"
  if [[ -z "$messages" ]]; then
    messages='[{"role":"user","content":"wp2-sensitive-prompt-sentinel"}]'
  fi
  curl --silent --show-error --fail --max-time 3 \
    --header 'X-WP2-Test-Sensitive: wp2-secret-sentinel' \
    --header 'Content-Type: application/json' \
    --data "{\"model\":\"${model}\",\"messages\":${messages},\"stream\":false}" \
    "${base_url}/compatible-mode/v1/chat/completions"
}

post_model stub-text | jq -e '.choices[0].message.content == "普通文本结果" and .choices[0].finish_reason == "stop"' >/dev/null
post_model stub-usage | jq -e '.usage.prompt_tokens == 10 and .usage.completion_tokens == 5 and .usage.total_tokens == 15' >/dev/null
post_model stub-missing-usage | jq -e 'has("usage") | not' >/dev/null
post_model stub-tool-call | jq -e '.choices[0].message.tool_calls[0].index == 0 and .choices[0].message.tool_calls[0].function.name == "query_tasks" and .choices[0].finish_reason == "tool_calls"' >/dev/null
post_model stub-multi-tool-calls | jq -e '(.choices[0].message.tool_calls | length == 2) and .choices[0].message.tool_calls[0].index == 0 and .choices[0].message.tool_calls[1].index == 1' >/dev/null
post_model stub-tool-call '[{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"query_tasks","arguments":"{}"}}]},{"role":"tool","tool_call_id":"call-1","content":"[]"}]' \
  | jq -e '.choices[0].message.content == "工具结果已分析"' >/dev/null
invalid_round_trip_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 3 \
  --header 'Content-Type: application/json' \
  --data '{"model":"stub-tool-call","messages":[{"role":"assistant","content":null,"tool_calls":[{"id":"call-1","type":"function","function":{"name":"query_tasks","arguments":"{}"}}]},{"role":"tool","tool_call_id":"call-1","content":"[]"}]}' \
  "${base_url}/compatible-mode/v1/chat/completions")"
ci_assert_equals "400" "$invalid_round_trip_status" "wp2_stub_missing_tool_index_not_rejected"
post_model stub-missing-choices | jq -e 'has("choices") | not' >/dev/null
post_model stub-invalid-arguments | jq -e '.choices[0].message.tool_calls[0].function.arguments == "not-json"' >/dev/null
post_model stub-empty | jq -e '.choices[0].message.content == null' >/dev/null

invalid_json="$(post_model stub-invalid-json)"
[[ "$invalid_json" == "{invalid-json" ]] || ci_fail "wp2_stub_invalid_json_mode_failed"

for status in 401 429 500 504; do
  actual_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 3 \
    --header 'Content-Type: application/json' \
    --data "{\"model\":\"stub-status-${status}\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}" \
    "${base_url}/compatible-mode/v1/chat/completions")"
  ci_assert_equals "$status" "$actual_status" "wp2_stub_status_${status}_failed"
done

set +e
curl --silent --output /dev/null --max-time 1 \
  --header 'Content-Type: application/json' \
  --data '{"model":"stub-timeout","messages":[{"role":"user","content":"x"}]}' \
  "${base_url}/compatible-mode/v1/chat/completions"
timeout_exit=$?
set -e
ci_assert_equals "28" "$timeout_exit" "wp2_stub_timeout_mode_failed"

if grep -Eq 'wp2-sensitive-prompt-sentinel|wp2-secret-sentinel' "$stub_log"; then
  ci_fail "wp2_stub_logged_sensitive_payload"
fi

ci_emit "wp2.stub.scenarios" "16"
ci_emit "wp2.stub.sensitivePayloadLogged" "false"
ci_emit "wp2.stub.status" "PASS"
