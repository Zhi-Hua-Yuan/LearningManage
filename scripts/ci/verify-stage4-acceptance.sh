#!/usr/bin/env bash

set -Eeuo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$project_root"

required=(
  docs/stage4/README.md
  docs/stage4/requirements/stage4-requirements-contract.md
  docs/stage4/requirements/write-path-coverage.md
  docs/stage4/requirements/published-migrations.sha256
  docs/stage4/acceptance/stage4-acceptance-contract.json
  docs/stage4/architecture/ADR-001-transactional-outbox.md
  docs/stage4/architecture/ADR-002-knowledge-document-visibility.md
  docs/stage4/architecture/ADR-003-embedding-and-vector-store.md
  docs/stage4/architecture/ADR-004-normalization-and-chunking.md
  docs/stage4/database/v4-data-dictionary.md
  docs/stage4/risk/risk-register.md
  src/main/resources/db/migration/V4__stage4_knowledge_index_and_outbox.sql
  sql/flyway/stage4/preflight_v4.sql
  sql/flyway/stage4/post_verify_v4.sql
  .github/workflows/stage4-knowledge-index.yml
)
for file in "${required[@]}"; do
  [[ -s "$file" ]] || { printf 'missing Stage 4 contract file: %s\n' "$file" >&2; exit 1; }
done

jq -e '
  .schemaVersion == 1 and .stage == "stage4" and
  (.status == "IN_PROGRESS" or .status == "PASS") and
  (.requiredGates | length) == 16 and
  .thresholds.businessOutboxAtomicityPercent == 100 and
  .thresholds.duplicatePointCount == 0 and
  .thresholds.staleOverwriteCount == 0 and
  .thresholds.privateFieldInTeamPointCount == 0 and
  .thresholds.embeddingDimension == 1024 and
  (.deferred | index("rag_query_and_citation")) != null and
  (.deferred | index("agent")) != null
' docs/stage4/acceptance/stage4-acceptance-contract.json >/dev/null

sha256sum --check docs/stage4/requirements/published-migrations.sha256
grep -Fq 'CREATE TABLE `ai_knowledge_index_event`' src/main/resources/db/migration/V4__stage4_knowledge_index_and_outbox.sql
grep -Fq 'FOR UPDATE SKIP LOCKED' src/main/java/com/spt/learningmanage/mapper/AiKnowledgeIndexEventMapper.java
grep -Fq 'Propagation.MANDATORY' src/main/java/com/spt/learningmanage/service/impl/KnowledgeIndexEventPublisherImpl.java
grep -Fq 'text-embedding-v4' src/main/resources/application.yml
grep -Fq 'qdrant/qdrant:v1.18.2' deploy/docker-compose.yml
grep -Fq 'IMPLEMENTATION_COMPLETE / ACCEPTANCE_DEFERRED' docs/stage3/README.md

printf 'stage4.acceptance.contract=PASS\n'
