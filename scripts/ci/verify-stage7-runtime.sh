#!/usr/bin/env bash
set -Eeuo pipefail

compose=(docker compose -f deploy/docker-compose.stage7-gate.yml)

wait_for_url() {
  local url="$1"
  local marker="$2"
  local body
  for _ in {1..30}; do
    if body="$("${compose[@]}" exec -T probe wget -q -O - "$url" 2>/dev/null)" \
        && grep -Fqi -- "$marker" <<<"$body"; then
      return 0
    fi
    sleep 1
  done
  echo "Stage 7 runtime endpoint did not become ready: $url" >&2
  return 1
}

wait_for_url http://backend:8123/api/health '"code":0'
wait_for_url http://backend:9123/actuator/health/liveness '"status":"UP"'
wait_for_url http://backend:9123/actuator/health/readiness '"status":"UP"'
wait_for_url http://backend:9123/actuator/prometheus 'learning_agent_queue_depth'
wait_for_url http://prometheus:9090/-/ready 'Prometheus Server is Ready'
wait_for_url http://grafana:3000/api/health '"database": "ok"'
wait_for_url http://tempo:3200/ready 'ready'

"${compose[@]}" exec -T ai-stub python - <<'PY'
import json
import urllib.request
doc = json.load(urllib.request.urlopen('http://backend:8123/api/v3/api-docs', timeout=10))
operations = sum(
    1 for path in doc.get('paths', {}).values()
    for method in path if method.lower() in {'get', 'post', 'put', 'patch', 'delete'}
)
if operations < 69:
    raise SystemExit(f'expected at least 69 runtime operations, got {operations}')
required = {
    '/admin/ai/ops/overview',
    '/admin/ai/ops/dependencies',
    '/admin/ai/ops/cleanup-runs',
    '/admin/ai/ops/cleanup-runs/{runId}',
    '/admin/ai/ops/cleanup-runs/{runId}/cancel',
}
missing = required - set(doc.get('paths', {}))
if missing:
    raise SystemExit(f'missing Stage 7 paths: {sorted(missing)}')
PY

"${compose[@]}" exec -T ai-stub python /opt/learning-ci/verify-stage7-enabled-ai.py

if "${compose[@]}" exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; then
  echo 'anonymous Redis access unexpectedly succeeded' >&2
  exit 1
fi
"${compose[@]}" exec -T redis sh -c \
  'redis-cli --user learning_app --pass "$REDIS_PASSWORD" --no-auth-warning ping' | grep -q PONG

if "${compose[@]}" config | grep -qE 'ports:.*9123|published: "9123"'; then
  echo 'management port must not be published' >&2
  exit 1
fi
