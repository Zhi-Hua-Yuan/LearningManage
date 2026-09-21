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
[[ "${#model_calls[@]}" -eq 2 ]] || ci_fail "ai_model_client_call_count_invalid:${#model_calls[@]}"
for model_call in "${model_calls[@]}"; do
  [[ "$model_call" == *'/ai/pipeline/AiInvocationPipeline.java:'* ]] \
    || ci_fail "ai_model_client_call_outside_pipeline"
done

# HTTP 传输能力下移到 legacy 适配器后，AiModelClientImpl（治理层）与业务层
# 都不再引用 AiHttpTransport；传输只允许出现在适配器边界内。
mapfile -t transport_imports < <(
  grep -RIn --include='*.java' \
    'import com.spt.learningmanage.client.ai.AiHttpTransport;' "$main_source" || true
)
unexpected_transport_imports=()
for import_line in "${transport_imports[@]}"; do
  if [[ "$import_line" != *'/client/ai/adapter/LegacyAiChatAdapter.java:'* ]]; then
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
    */client/ai/AiHttpTransport.java|*/client/ai/HutoolAiHttpTransport.java|*/client/ai/adapter/LegacyAiChatAdapter.java) ;;
    *) unexpected_transport_references+=("$source_file") ;;
  esac
done
[[ "${#unexpected_transport_references[@]}" -eq 0 ]] \
  || ci_fail "ai_http_transport_reference_outside_boundary"

# 框架不得渗入业务层：org.springframework.ai 只允许出现在 Spring AI
# chat/embedding/structured-output adapters 与它们的配置类里。这条把
# 「换协议不影响治理」变成可证伪的约束。
mapfile -t spring_ai_imports < <(
  grep -RIn --include='*.java' 'import org\.springframework\.ai\.' "$main_source" || true
)
unexpected_spring_ai_imports=()
for import_line in "${spring_ai_imports[@]}"; do
  case "$import_line" in
    */client/ai/adapter/SpringAiChatAdapter.java:*|*/client/ai/adapter/SpringAiStreamingModelClient.java:*|*/client/knowledge/SpringAiEmbeddingModel.java:*|*/service/impl/SpringAiEmbeddingClient.java:*|*/ai/pipeline/AiStructuredOutputDecoder.java:*|*/config/SpringAiChatConfiguration.java:*) ;;
    *) unexpected_spring_ai_imports+=("$import_line") ;;
  esac
done
[[ "${#unexpected_spring_ai_imports[@]}" -eq 0 ]] \
  || ci_fail "spring_ai_import_outside_adapter_boundary"

ci_emit "ai.boundary.modelClientCalls" "${#model_calls[@]}"
ci_emit "ai.boundary.businessDirectModelCalls" "0"
ci_emit "ai.boundary.businessDirectTransportDependencies" "0"
ci_emit "ai.boundary.springAiImports" "${#spring_ai_imports[@]}"
ci_emit "ai.boundary.status" "PASS"
