#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/ci-common.sh"
ci_require_command grep

main_source="${project_root}/src/main/java"

mapfile -t model_references < <(
  grep -RIl --include='*.java' 'AiModelClient' "$main_source" || true
)
unexpected_model_references=()
for source_file in "${model_references[@]}"; do
  case "$source_file" in
    */ai/pipeline/AiInvocationPipeline.java|*/service/AiModelClient.java|*/service/impl/AiModelClientImpl.java) ;;
    *) unexpected_model_references+=("$source_file") ;;
  esac
done
[[ "${#unexpected_model_references[@]}" -eq 0 ]] \
  || ci_fail "ai_model_client_dependency_outside_boundary"

mapfile -t model_calls < <(
  grep -RInE --include='*.java' 'aiModelClient\.(chat|invoke)\(' "$main_source" || true
)
[[ "${#model_calls[@]}" -eq 1 ]] || ci_fail "ai_model_client_call_count_invalid:${#model_calls[@]}"
[[ "${model_calls[0]}" == *'/ai/pipeline/AiInvocationPipeline.java:'* ]] \
  || ci_fail "ai_model_client_call_outside_pipeline"

mapfile -t transport_imports < <(
  grep -RIn --include='*.java' \
    'import com.spt.learningmanage.client.ai.AiHttpTransport;' "$main_source" || true
)
unexpected_transport_imports=()
for import_line in "${transport_imports[@]}"; do
  if [[ "$import_line" != *'/service/impl/AiModelClientImpl.java:'* ]]; then
    unexpected_transport_imports+=("$import_line")
  fi
done
[[ "${#unexpected_transport_imports[@]}" -eq 0 ]] \
  || ci_fail "ai_http_transport_dependency_outside_adapter"

mapfile -t transport_references < <(
  grep -RIl --include='*.java' 'AiHttpTransport' "$main_source" || true
)
unexpected_transport_references=()
for source_file in "${transport_references[@]}"; do
  case "$source_file" in
    */client/ai/AiHttpTransport.java|*/client/ai/HutoolAiHttpTransport.java|*/service/impl/AiModelClientImpl.java) ;;
    *) unexpected_transport_references+=("$source_file") ;;
  esac
done
[[ "${#unexpected_transport_references[@]}" -eq 0 ]] \
  || ci_fail "ai_http_transport_reference_outside_boundary"

ci_emit "ai.boundary.modelClientCalls" "${#model_calls[@]}"
ci_emit "ai.boundary.businessDirectModelCalls" "0"
ci_emit "ai.boundary.businessDirectTransportDependencies" "0"
ci_emit "ai.boundary.status" "PASS"
