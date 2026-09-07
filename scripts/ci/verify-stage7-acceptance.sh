#!/usr/bin/env bash
set -Eeuo pipefail

node <<'NODE'
const fs = require('node:fs');
const contract = JSON.parse(fs.readFileSync('docs/stage7/acceptance/stage7-acceptance-contract.json', 'utf8'));
if (contract.stage !== 7 || contract.required.databaseVersion !== 8) {
  throw new Error('Stage 7 acceptance identity mismatch');
}
if (contract.required.frontendApiOperations !== 60
    || contract.required.runtimeApiMinimumOperations !== 69) {
  throw new Error('Stage 7 API contract counts changed');
}
if (contract.required.sensitiveTelemetryFindings !== 0
    || contract.required.highCardinalityMetricLabels !== 0) {
  throw new Error('Stage 7 telemetry safety thresholds must remain zero');
}
NODE

grep -Fq 'AI_CLEANUP_ENABLED=false' .env.example
grep -Fq 'AI_CLEANUP_SCHEDULE_ENABLED=false' .env.example
grep -Fq 'MANAGEMENT_ADDRESS=127.0.0.1' .env.example
grep -Fq 'CREATE TABLE `ai_data_cleanup_run`' src/main/resources/db/migration/V8__stage7_observability_and_data_lifecycle.sql
grep -Fq 'approved_dry_run_id' src/main/resources/db/migration/V8__stage7_observability_and_data_lifecycle.sql
grep -Fq 'user default off' deploy/redis/redis-entrypoint-stage7.sh

if grep -R -E 'tags\([^)]*(userId|projectId|teamId|runId|requestId|traceId)' \
    src/main/java/com/spt/learningmanage/observability; then
  echo 'high-cardinality metric label detected' >&2
  exit 1
fi

python3 - <<'PY'
import json
from pathlib import Path
files = sorted(Path('deploy/observability/grafana/dashboards').glob('*.json'))
if len(files) != 6:
    raise SystemExit(f'expected 6 Grafana dashboards, got {len(files)}')
for path in files:
    data = json.loads(path.read_text(encoding='utf-8'))
    if not data.get('title') or not data.get('uid') or not data.get('panels'):
        raise SystemExit(f'invalid dashboard: {path}')
json.loads(Path('docs/stage7/acceptance/stage7-acceptance-contract.json').read_text(encoding='utf-8'))
PY
