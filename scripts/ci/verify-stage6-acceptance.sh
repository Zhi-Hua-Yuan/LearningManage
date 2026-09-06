#!/usr/bin/env bash
set -Eeuo pipefail

node <<'NODE'
const fs = require('node:fs');
const contract = JSON.parse(fs.readFileSync('docs/stage6/acceptance/stage6-acceptance-contract.json', 'utf8'));
if (contract.stage !== 6 || contract.name !== 'controlled-asynchronous-agent') {
  throw new Error('Stage 6 acceptance identity mismatch');
}
if (contract.required.readOnlyRegisteredTools !== 6 || contract.required.maximumToolCalls !== 4) {
  throw new Error('Stage 6 Tool boundary changed');
}
if (contract.acceptanceThresholds.unauthorizedToolCalls !== 0
    || contract.acceptanceThresholds.directBusinessWrites !== 0
    || contract.acceptanceThresholds.publicPrivacyLeaks !== 0) {
  throw new Error('Stage 6 security thresholds must remain zero');
}
NODE

grep -Fq 'AI_AGENT_ENABLED=false' .env.example
grep -Fq 'AI_AGENT_WORKER_ENABLED=false' .env.example
grep -Fq 'AI_AGENT_TOOL_CALLING_ENABLED=false' .env.example
