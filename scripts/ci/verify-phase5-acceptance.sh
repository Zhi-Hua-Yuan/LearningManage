#!/usr/bin/env bash
set -Eeuo pipefail

node evals/phase5/scripts/validate-matrix.mjs >/dev/null

node <<'NODE'
const fs = require('node:fs');
const contract = JSON.parse(fs.readFileSync('docs/phase5-ai-eval/acceptance-manifest.json', 'utf8'));
if (contract.phase !== 'phase5-ai-eval') throw new Error('Phase 5 identity mismatch');
if (contract.migrationHead !== 'V9') throw new Error('Phase 5 migration head must be V9');
if (contract.defaultAdapter !== 'spring-ai') throw new Error('Spring AI must be the Phase 5 target default');
if (contract.core.scenes !== 5 || contract.core.promptCodes !== 6) throw new Error('Core AI coverage drift');
if (contract.core.qualityCases !== 170 || contract.core.failureInjectionCases !== 40) throw new Error('Core dataset drift');
if (contract.core.developmentRounds !== 1 || contract.core.regressionRounds !== 3 || contract.core.holdoutRounds !== 3) {
  throw new Error('Core round matrix drift');
}
if (contract.rag.positiveCases !== 50 || contract.rag.regressionCases !== 30 || contract.rag.holdoutCases !== 20) {
  throw new Error('RAG dataset split drift');
}
if (contract.rag.recallAt5Minimum < 0.8 || contract.rag.permissionLeaksMaximum !== 0 || contract.rag.staleResultsMaximum !== 0) {
  throw new Error('RAG security threshold weakened');
}
if (contract.agent.maximumToolCalls !== 4 || contract.agent.directBusinessWritesMaximum !== 0) {
  throw new Error('Agent safety threshold weakened');
}
if (contract.humanReview.sampleRate < 0.2 || contract.humanReview.agreementMinimum < 0.8) {
  throw new Error('Human review threshold weakened');
}
if (contract.productionObservationDays !== 7) throw new Error('Production observation window drift');
if (contract.productionObservationThresholds.failureRateRegressionMaximum > 0.05
    || contract.productionObservationThresholds.p95LatencyMultiplierMaximum > 1.2
    || contract.productionObservationThresholds.averageCostMultiplierMaximum > 1.2
    || contract.productionObservationThresholds.safetyIncidentsMaximum !== 0
    || contract.productionObservationThresholds.permissionLeaksMaximum !== 0
    || contract.productionObservationThresholds.formalBusinessWritesMaximum !== 0) {
  throw new Error('Production observation thresholds weakened');
}
NODE

grep -Fq 'AI_CHAT_ADAPTER=spring-ai' .env.example
grep -Fq 'AI_RAG_ENABLED=true' .env.example
grep -Fq 'AI_AGENT_ENABLED=true' .env.example
grep -Fq 'AI_AGENT_WORKER_ENABLED=true' .env.example
grep -Fq 'AI_AGENT_TOOL_CALLING_ENABLED=true' .env.example
grep -Fq 'AI_CLEANUP_ENABLED=false' .env.example

printf 'phase5.acceptance=PASS\n'
