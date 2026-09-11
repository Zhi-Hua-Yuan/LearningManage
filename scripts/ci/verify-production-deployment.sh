#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
cd "$project_root"

python_cmd=python3
if ! "$python_cmd" --version >/dev/null 2>&1; then
  python_cmd=python
fi
"$python_cmd" --version >/dev/null 2>&1 \
  || { printf '%s\n' 'a working python3 or python is required' >&2; exit 1; }

required_files=(
  deploy/docker-compose.prod.yml
  deploy/docker-compose.prod-gate.yml
  deploy/Dockerfile.backend.prod
  deploy/Dockerfile.backend.prod.dockerignore
  deploy/Dockerfile.frontend.prod
  deploy/Dockerfile.frontend.prod.dockerignore
  deploy/nginx.prod.conf
  deploy/nginx.host.http.conf
  deploy/nginx.host.conf
  deploy/learning.env.example
  deploy/release-images.env.example
  deploy/observability.env.example
  deploy/backup.env.example
  deploy/migration.env.example
  deploy/mysql/my.cnf
  deploy/apt/20auto-upgrades
  deploy/apt/52unattended-upgrades-local
  deploy/sshd/99-learning-manage.conf
  deploy/sshd/10-learning-manage-bootstrap.conf
  deploy/sudoers/90-learning-manage
  deploy/systemd/learning-manage-backup.service
  deploy/systemd/learning-manage-backup.timer
  deploy/redis/redis-entrypoint-prod.sh
  deploy/observability/tempo.prod.yml
  deploy/production-release-manifest.schema.json
)
for file in "${required_files[@]}"; do
  [[ -s "$file" ]] || { printf 'missing production file: %s\n' "$file" >&2; exit 1; }
done

find scripts/prod -maxdepth 1 -type f -name '*.sh' -print0 | xargs -0 -n1 bash -n
while IFS= read -r -d '' script; do
  if git ls-files --error-unmatch "$script" >/dev/null 2>&1; then
    mode="$(git ls-files --stage "$script" | awk '{print $1}')"
    [[ "$mode" == 100755 ]] \
      || { printf 'production script must be executable in Git: %s has %s\n' "$script" "$mode" >&2; exit 1; }
  fi
done < <(find scripts/prod -maxdepth 1 -type f -name '*.sh' -print0)

if grep -Eq '^[[:space:]]*(container_name|build):' deploy/docker-compose.prod.yml; then
  printf '%s\n' 'production Compose must not contain container_name or build' >&2
  exit 1
fi
if grep -E '^[[:space:]]*image:' deploy/docker-compose.prod.yml | grep -Eq '(:latest|:-[^}]*latest)'; then
  printf '%s\n' 'floating latest image detected' >&2
  exit 1
fi

grep -Fq '127.0.0.1:18080:80' deploy/docker-compose.prod.yml
grep -Fq '127.0.0.1:13000:3000' deploy/docker-compose.prod.yml
grep -Fq 'FLYWAY_ENABLED: "false"' deploy/docker-compose.prod.yml
grep -Fq 'profiles: ["admin"]' deploy/docker-compose.prod.yml
grep -Fq 'profiles: ["observability"]' deploy/docker-compose.prod.yml
grep -Fq 'QDRANT_REQUIRE_SECURE_TRANSPORT: "false"' deploy/docker-compose.prod.yml
grep -Fq '!frontend/dist/**' deploy/Dockerfile.frontend.prod.dockerignore
grep -Fq 'block_retention: 24h' deploy/observability/tempo.prod.yml
grep -Fq -- '--storage.tsdb.retention.time=3d' deploy/docker-compose.prod.yml
grep -Fq -- '--storage.tsdb.retention.size=512MB' deploy/docker-compose.prod.yml
grep -Fq 'innodb_buffer_pool_size=256M' deploy/mysql/my.cnf
grep -Fq 'max_connections=50' deploy/mysql/my.cnf
grep -Fq 'CREATE TEMPORARY TABLES' scripts/prod/provision-database.sh
grep -Fq 'verify-production-secrets.sh' scripts/prod/deploy.sh
grep -Fq 'must be false for a new Phase A deployment' scripts/prod/deploy.sh
grep -Fq 'TZ=Asia/Shanghai date +%u' scripts/prod/backup-mysql.sh
grep -Fq 'restored task row count does not match backup metadata' scripts/prod/restore-drill.sh
grep -Fq 'AI_CLEANUP_SCHEDULE_ENABLED=false' scripts/prod/rollback.sh
grep -Fq 'lm_sync_release_environment' scripts/prod/rollback.sh
grep -Fq 'lm_acquire_operation_lock' scripts/prod/common.sh
grep -Fq 'smoke-test.sh' scripts/prod/deploy.sh
grep -Fq 'memory usage must remain below' scripts/prod/verify-host.sh
grep -Fq 'unexpected UFW allow target' scripts/prod/verify-host.sh
grep -Fq 'git -C "$LM_RELEASE_DIR" archive' scripts/prod/assemble-release-bundle.sh
if grep -Fq 'cp -a "$LM_RELEASE_DIR/deploy"' scripts/prod/assemble-release-bundle.sh; then
  printf '%s\n' 'release assembler must not copy the entire working-tree deploy directory' >&2
  exit 1
fi
grep -Fq 'user default off' deploy/redis/redis-entrypoint-prod.sh
grep -Fq '~rate_limit:ai:* +@connection +eval +evalsha +incr +expire' deploy/redis/redis-entrypoint-prod.sh
grep -Fq 'maxmemory-policy noeviction' deploy/redis/redis-entrypoint-prod.sh
grep -Fq 'OnCalendar=*-*-* 01:30:00 Asia/Shanghai' deploy/systemd/learning-manage-backup.timer
grep -Fq 'User=lmdeploy' deploy/systemd/learning-manage-backup.service
grep -Fq 'AuthenticationMethods publickey' deploy/sshd/10-learning-manage-bootstrap.conf
grep -Fq 'lmdeploy ALL=(ALL:ALL) NOPASSWD: ALL' deploy/sudoers/90-learning-manage

for setting in \
  AI_MAX_CONCURRENT_CALLS=4 \
  AI_KNOWLEDGE_CLAIM_BATCH_SIZE=5 \
  AI_KNOWLEDGE_WORKER_CONCURRENCY=1 \
  AI_KNOWLEDGE_EMBEDDING_CONCURRENCY=1 \
  AI_KNOWLEDGE_VECTOR_CONCURRENCY=2 \
  AI_AGENT_BATCH_SIZE=1 \
  AI_AGENT_MAX_CONCURRENT_RUNS=1 \
  AI_AGENT_MAX_CONCURRENT_RUNS_PER_USER=1 \
  AI_RERANK_MAX_CONCURRENT_CALLS=1 \
  MANAGEMENT_TRACING_ENABLED=false \
  MANAGEMENT_TRACING_SAMPLING=0.02 \
  AI_CLEANUP_BATCH_SIZE=100 \
  AI_CLEANUP_MAX_RUNTIME_SECONDS=300; do
  grep -Fqx "$setting" deploy/learning.env.example
done

for disabled in \
  AI_KNOWLEDGE_WORKER_ENABLED=false \
  AI_RAG_ENABLED=false \
  AI_AGENT_ENABLED=false \
  AI_AGENT_WORKER_ENABLED=false \
  AI_AGENT_TOOL_CALLING_ENABLED=false \
  AI_CLEANUP_ENABLED=false \
  AI_CLEANUP_SCHEDULE_ENABLED=false; do
  grep -Fqx "$disabled" deploy/learning.env.example
done

image_count="$(grep -Ec '^[A-Z_]+_IMAGE=[^[:space:]]+@sha256:[0-9a-f]{64}$' deploy/learning.env.example)"
[[ "$image_count" == 8 ]] || { printf 'expected 8 digest-pinned external images, got %s\n' "$image_count" >&2; exit 1; }
release_image_count="$(grep -Ec '^[A-Z_]+_IMAGE=[^[:space:]]+@sha256:[0-9a-f]{64}$' deploy/release-images.env.example)"
[[ "$release_image_count" == 8 ]] || { printf 'expected 8 release image digests, got %s\n' "$release_image_count" >&2; exit 1; }

while IFS='=' read -r key value; do
  [[ "$key" == RUNTIME_IMAGE ]] && continue
  grep -Fq "CI_${key}: ${value}" .github/workflows/stage7-production-ops.yml \
    || { printf '%s\n' "$key does not match the Stage 7 Gate inventory" >&2; exit 1; }
done < <(grep -E '^[A-Z_]+_IMAGE=' deploy/release-images.env.example)
runtime_image="$(awk -F= '$1 == "RUNTIME_IMAGE" { print substr($0, index($0, "=") + 1) }' deploy/release-images.env.example)"
grep -Fq -- "--build-arg RUNTIME_IMAGE=${runtime_image}" .github/workflows/release-gate.yml \
  || { printf '%s\n' 'RUNTIME_IMAGE does not match the Release Gate' >&2; exit 1; }
grep -Fq 'bash scripts/prod/create-production-manifest.sh' .github/workflows/release-gate.yml
grep -Fq 'production-release-manifest.json' .github/workflows/release-gate.yml
grep -Fq 'CI_PRODUCTION_ENV_FILE: ${{ github.workspace }}/learning-manage-production-gate.env' \
  .github/workflows/release-gate.yml
if grep -Fq 'CI_PRODUCTION_ENV_FILE: ${{ runner.temp }}' .github/workflows/release-gate.yml; then
  printf '%s\n' 'job-level production env path must not use the unavailable runner context' >&2
  exit 1
fi
grep -Fq -- '--file deploy/Dockerfile.backend.prod' .github/workflows/release-gate.yml
grep -Fq -- '--file deploy/Dockerfile.frontend.prod' .github/workflows/release-gate.yml
grep -Fq 'org.springframework.boot.loader.launch.PropertiesLauncher' .github/workflows/release-gate.yml
grep -Fq 'run_production_migrator migrate' .github/workflows/release-gate.yml
grep -Fq 'CREATE TEMPORARY TABLES' .github/workflows/release-gate.yml
grep -Fq 'Run production frontend image with hardened runtime options' .github/workflows/release-gate.yml
grep -Fq "label=com.docker.compose.network=ci-edge-access" .github/workflows/release-gate.yml
grep -Fq 'docker create' .github/workflows/release-gate.yml
grep -Fq 'docker network connect "$internal_network_id"' .github/workflows/release-gate.yml
grep -Fq 'docker start "$CI_PRODUCTION_FRONTEND_CONTAINER"' .github/workflows/release-gate.yml
grep -Fq 'Run full production Compose Phase A gate' .github/workflows/release-gate.yml
grep -Fq -- '--unset=DB_NAME' \
  .github/workflows/release-gate.yml
grep -Fq -- '--unset=FLYWAY_DB_USERNAME' .github/workflows/release-gate.yml
grep -Fq -- '--unset=FLYWAY_DB_PASSWORD' .github/workflows/release-gate.yml
if grep -Fq 'unset DB_NAME FLYWAY_DB_USERNAME FLYWAY_DB_PASSWORD' \
    .github/workflows/release-gate.yml; then
  printf '%s\n' 'production Compose isolation must not strip later CI safety variables' >&2
  exit 1
fi
grep -Fq 'redis-cli -e --user learning_app' .github/workflows/release-gate.yml
grep -Fq "grep -q 'NOPERM'" .github/workflows/release-gate.yml

tmp_dir="$(mktemp -d)"
trap '[[ "$tmp_dir" == /tmp/* ]] && rm -rf -- "$tmp_dir"' EXIT
printf '%s\n' root_secret_123456789012345678901234567890 > "$tmp_dir/mysql-root"
printf '%s\n' grafana_secret_1234567890123456789012345 > "$tmp_dir/grafana-admin"
printf '%s\n' \
  'MYSQL_ROOT_PASSWORD_FILE=REPLACE_MYSQL_ROOT_FILE' \
  'DB_PASSWORD=db_secret_123456789012345678901234567890' \
  'REDIS_PASSWORD=redis_secret_123456789012345678901234567' \
  'JWT_SECRET=jwt_secret_123456789012345678901234567890' \
  'ALIYUN_API_KEY=sk-ci-provider-key-1234567890' \
  'AI_EMBEDDING_API_KEY=sk-ci-provider-key-1234567890' \
  'AI_RERANK_API_KEY=sk-ci-provider-key-1234567890' \
  'AI_RAG_QUESTION_HMAC_SECRET=rag_secret_123456789012345678901234567890' \
  'QDRANT_API_KEY=qdrant_secret_12345678901234567890123456' \
  > "$tmp_dir/production.env"
sed -i "s|REPLACE_MYSQL_ROOT_FILE|$tmp_dir/mysql-root|" "$tmp_dir/production.env"
printf '%s\n' \
  'FLYWAY_DB_USERNAME=learning_manage_migrator' \
  'FLYWAY_DB_PASSWORD=migrator_secret_1234567890123456789012345' \
  > "$tmp_dir/migration.env"
printf 'GRAFANA_ADMIN_PASSWORD_FILE=%s\n' "$tmp_dir/grafana-admin" > "$tmp_dir/observability.env"
chmod 0600 "$tmp_dir"/*.env "$tmp_dir/mysql-root" "$tmp_dir/grafana-admin"

LM_ENV_FILE="$tmp_dir/production.env" \
LM_MIGRATION_ENV_FILE="$tmp_dir/migration.env" \
LM_OBSERVABILITY_ENV_FILE="$tmp_dir/observability.env" \
LM_ALLOW_TEST_FILE_MODES=true \
  bash scripts/prod/verify-production-secrets.sh --with-migration --with-observability

cp "$tmp_dir/production.env" "$tmp_dir/placeholder.env"
sed -i 's/^DB_PASSWORD=.*/DB_PASSWORD=REPLACE_WITH_APPLICATION_DATABASE_PASSWORD/' "$tmp_dir/placeholder.env"
if LM_ENV_FILE="$tmp_dir/placeholder.env" LM_ALLOW_TEST_FILE_MODES=true \
    bash scripts/prod/verify-production-secrets.sh >/dev/null 2>&1; then
  printf '%s\n' 'placeholder production secret was not rejected' >&2
  exit 1
fi

export BACKEND_IMAGE=learningmanage-backend:1111111111111111111111111111111111111111
export FRONTEND_IMAGE=learningmanage-frontend:2222222222222222222222222222222222222222
export MYSQL_IMAGE=mysql:8.0.41@sha256:bf577825b52ab281d6281fb281eabbfdc73507eda8f2c2745790251533ef0306
export REDIS_IMAGE=redis:7.4-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf
export QDRANT_IMAGE=qdrant/qdrant:v1.18.2@sha256:75eab8c4ba42096724fdcfde8b4de0b5713d529dde32f285a1f86fdcb2c9e50c
export PROMETHEUS_IMAGE=prom/prometheus:v2.54.1@sha256:f6639335d34a77d9d9db382b92eeb7fc00934be8eae81dbc03b31cfe90411a94
export TEMPO_IMAGE=grafana/tempo:2.6.1@sha256:ef4384fce6e8ad22b95b243d8fc165628cda655376fd50e7850536ad89d71d50
export GRAFANA_IMAGE=grafana/grafana:11.2.2@sha256:d5133220d770aba5cb655147b619fa8770b90f41d8489a821d33b1cd34d16f89
export CI_AI_STUB_IMAGE=python:3.12.8-alpine@sha256:ba13ef990f6e5d13014e9e8d04c02a8fdb0fe53d6dccf6e19147f316e6cc3a84
export MYSQL_ROOT_PASSWORD_FILE="$tmp_dir/mysql-root"
export GRAFANA_ADMIN_PASSWORD_FILE="$tmp_dir/grafana-admin"
export DB_NAME=learning_manage
export DB_USERNAME=learning_manage_app
export DB_PASSWORD=ci_application_password_1234567890
export REDIS_PASSWORD=ci_redis_password_123456789012345
export JWT_SECRET=ci_jwt_secret_with_at_least_32_bytes
export ALIYUN_API_KEY=ci-only-not-a-real-key
export AI_EMBEDDING_API_KEY=ci-only-not-a-real-key
export AI_RERANK_API_KEY=ci-only-not-a-real-key
export AI_RAG_QUESTION_HMAC_SECRET=ci_rag_hmac_secret_with_at_least_32_bytes
export QDRANT_API_KEY=ci_qdrant_key_with_at_least_32_bytes
export FLYWAY_DB_USERNAME=learning_manage_migrator
export FLYWAY_DB_PASSWORD=ci_migrator_password_1234567890123

docker compose \
  --project-name learning-manage-production-contract \
  --env-file /dev/null \
  --file deploy/docker-compose.prod.yml \
  --profile admin \
  --profile observability \
  config --format json > "$tmp_dir/compose.json"
docker compose \
  --project-name learning-manage-production-contract \
  --env-file /dev/null \
  --file deploy/docker-compose.prod.yml \
  --file deploy/docker-compose.prod-gate.yml \
  --profile admin \
  --profile observability \
  config --quiet

"$python_cmd" - "$tmp_dir/compose.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    config = json.load(stream)

services = config["services"]
expected = {
    "backend": 896 * 1024 * 1024,
    "mysql": 640 * 1024 * 1024,
    "qdrant": 512 * 1024 * 1024,
    "redis": 128 * 1024 * 1024,
    "frontend": 64 * 1024 * 1024,
    "prometheus": 256 * 1024 * 1024,
    "tempo": 192 * 1024 * 1024,
    "grafana": 256 * 1024 * 1024,
}
for name, limit in expected.items():
    actual = services[name].get("mem_limit")
    if int(actual) != limit:
        raise SystemExit(f"{name} mem_limit: expected {limit}, got {actual}")

for name, service in services.items():
    if "build" in service or "container_name" in service:
        raise SystemExit(f"forbidden production key on {name}")

published = []
for name, service in services.items():
    for port in service.get("ports", []):
        published.append((name, port.get("host_ip"), int(port["published"]), int(port["target"])))
expected_ports = sorted([
    ("frontend", "127.0.0.1", 18080, 80),
    ("grafana", "127.0.0.1", 13000, 3000),
])
if sorted(published) != expected_ports:
    raise SystemExit(f"unexpected published ports: {published}")

networks = config["networks"]
for name in ("data-internal", "observability-internal"):
    if networks[name].get("internal") is not True:
        raise SystemExit(f"{name} must be internal")

backend = services["backend"]
if backend["environment"]["FLYWAY_ENABLED"] != "false":
    raise SystemExit("backend Flyway must remain disabled")
if services["migrator"].get("profiles") != ["admin"]:
    raise SystemExit("migrator must remain admin-only")
PY

"$python_cmd" -m json.tool deploy/production-release-manifest.schema.json >/dev/null

grep -Fq 'proxy_read_timeout 150s;' deploy/nginx.prod.conf
grep -Fq 'try_files $uri $uri/ /index.html;' deploy/nginx.prod.conf
grep -Fq 'return 404;' deploy/nginx.prod.conf
grep -Fq '127.0.0.1:18080' deploy/nginx.host.conf
grep -Fq 'limit_req zone=lm_login' deploy/nginx.host.conf
grep -Fq 'limit_req zone=lm_register' deploy/nginx.host.conf
grep -Fq 'limit_req_status 429;' deploy/nginx.host.conf
grep -Fq 'ssl_certificate /etc/letsencrypt/live/__DOMAIN__/fullchain.pem;' deploy/nginx.host.conf

printf '%s\n' 'production deployment contract passed'
