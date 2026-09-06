#!/usr/bin/env bash
set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root_dir"

required_files=(
  src/main/resources/db/migration/V5__stage5_permission_aware_rag.sql
  src/main/java/com/spt/learningmanage/controller/RagController.java
  src/main/java/com/spt/learningmanage/service/impl/RagServiceImpl.java
  src/main/java/com/spt/learningmanage/service/rag/RagCandidateHydrator.java
  src/main/java/com/spt/learningmanage/service/rag/RagCitationValidator.java
  src/main/java/com/spt/learningmanage/service/impl/AliyunRerankClient.java
  src/test/java/com/spt/learningmanage/integration/RagEndToEndIT.java
  evals/stage5/promptfooconfig.yaml
  evals/stage5/tests/generated.json
  docs/stage5/acceptance/stage5-acceptance-contract.json
)
for file in "${required_files[@]}"; do
  [[ -f "$file" ]] || { echo "missing Stage 5 artifact: $file" >&2; exit 1; }
done

grep -Fq '@PostMapping("/ask")' src/main/java/com/spt/learningmanage/controller/RagController.java
grep -Fq '@GetMapping("/result/{requestId}")' src/main/java/com/spt/learningmanage/controller/RagController.java
grep -Fq 'filterReadableTaskIds' src/main/java/com/spt/learningmanage/service/rag/RagCandidateHydrator.java
grep -Fq 'filterReadableWeeklyReviewIds' src/main/java/com/spt/learningmanage/service/rag/RagCandidateHydrator.java
grep -Fq 'AiContentLoggingPolicy.METADATA_ONLY' src/main/java/com/spt/learningmanage/service/rag/RagAnswerService.java
grep -Fq 'RAG_PROJECT_ANSWER' src/main/java/com/spt/learningmanage/prompt/DefaultAiPromptTemplateProvider.java

node - <<'NODE'
const fs = require('node:fs');
const acceptance = JSON.parse(fs.readFileSync('docs/stage5/acceptance/stage5-acceptance-contract.json', 'utf8'));
const tests = JSON.parse(fs.readFileSync('evals/stage5/tests/generated.json', 'utf8'));
if (acceptance.stage !== 5 || acceptance.required.promptfooCases !== 50) process.exit(1);
if (acceptance.required.crossTenantLeaksMaximum !== 0) process.exit(1);
if (tests.length !== 50) process.exit(1);
if (tests.filter((item) => item.vars.split === 'regression').length !== 30) process.exit(1);
if (tests.filter((item) => item.vars.split === 'holdout').length !== 20) process.exit(1);
NODE

echo "Stage 5 repository acceptance contract passed"
