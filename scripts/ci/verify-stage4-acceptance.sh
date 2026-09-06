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
  docs/stage4/acceptance/stage4-deterministic-evidence-manifest.json
  docs/stage4/acceptance/stage4-deterministic-evidence-manifest.json.sha256
  docs/stage4/architecture/ADR-001-transactional-outbox.md
  docs/stage4/architecture/ADR-002-knowledge-document-visibility.md
  docs/stage4/architecture/ADR-003-embedding-and-vector-store.md
  docs/stage4/architecture/ADR-004-normalization-and-chunking.md
  docs/stage4/database/v4-data-dictionary.md
  docs/stage4/release/stage4-release-candidate-manifest.json
  docs/stage4/release/stage4-release-candidate-manifest.json.sha256
  docs/stage4/risk/risk-register.md
  src/main/resources/db/migration/V4__stage4_knowledge_index_and_outbox.sql
  sql/flyway/stage4/preflight_v4.sql
  sql/flyway/stage4/post_verify_v4.sql
  .github/workflows/stage4-knowledge-index.yml
  .github/workflows/stage4-real-embedding.yml
)
for file in "${required[@]}"; do
  [[ -s "$file" ]] || { printf 'missing Stage 4 contract file: %s\n' "$file" >&2; exit 1; }
done

jq -e '
  .schemaVersion == 1 and .stage == "stage4" and
  (.status == "IN_PROGRESS" or .status == "IMPLEMENTATION_COMPLETE" or .status == "PASS") and
  (.requiredGates | length) == 16 and
  .thresholds.businessOutboxAtomicityPercent == 100 and
  .thresholds.duplicatePointCount == 0 and
  .thresholds.staleOverwriteCount == 0 and
  .thresholds.privateFieldInTeamPointCount == 0 and
  .thresholds.embeddingDimension == 1024 and
  (.deferred | index("rag_query_and_citation")) != null and
  (.deferred | index("agent")) != null
' docs/stage4/acceptance/stage4-acceptance-contract.json >/dev/null

jq -e '
  .schemaVersion == 1 and .stage == "stage4" and .status == "DETERMINISTIC_PASS" and
  .protectedRealEmbeddingStatus == "PENDING" and
  (.sourceCommitSha | test("^[0-9a-f]{40}$")) and
  .verified.backendTestCount == 766 and
  .verified.stage4EndToEndTestCount == 5 and
  .verified.embeddingDimension == 1024 and
  .verified.duplicatePointCount == 0 and
  .verified.backfillDifferenceCount == 0 and
  .verified.externalFailureBusinessRollbackCount == 0 and
  .verified.unauthorizedPointCountAfterAccessContraction == 0
' docs/stage4/acceptance/stage4-deterministic-evidence-manifest.json >/dev/null

jq -e '
  .schemaVersion == 1 and .stage == "stage4" and .status == "PASS" and
  .releaseEligibility == "ELIGIBLE" and
  (.validatedSourceSha | test("^[0-9a-f]{40}$")) and
  .plannedTag == "stage4-v1.0.0" and
  .deterministicEvidence.sha256 == "04eefa250be3ca3e64b8255248bf9a60bbbfe6b06af99306c1f7aefd72964a1d" and
  .protectedProviderEvidence.runId == 34007915622 and
  .protectedProviderEvidence.result == "PASS" and
  .protectedProviderEvidence.model == "text-embedding-v4" and
  .protectedProviderEvidence.syntheticInputCount == 10 and
  .protectedProviderEvidence.embeddingDimension == 1024 and
  .protectedProviderEvidence.usagePresent == true and
  .acceptance.backendTestCount == 766 and
  .acceptance.stage4EndToEndTestCount == 5
' docs/stage4/release/stage4-release-candidate-manifest.json >/dev/null

(cd docs/stage4/release && sha256sum --check stage4-release-candidate-manifest.json.sha256)

(cd docs/stage4/acceptance && sha256sum --check stage4-deterministic-evidence-manifest.json.sha256)
jq -r '.sourceFiles | to_entries[] | "\(.value)  \(.key)"' \
  docs/stage4/acceptance/stage4-deterministic-evidence-manifest.json \
  | sha256sum --check

sha256sum --check docs/stage4/requirements/published-migrations.sha256
grep -Fq 'CREATE TABLE `ai_knowledge_index_event`' src/main/resources/db/migration/V4__stage4_knowledge_index_and_outbox.sql
grep -Fq 'FOR UPDATE SKIP LOCKED' src/main/java/com/spt/learningmanage/mapper/AiKnowledgeIndexEventMapper.java
grep -Fq 'Propagation.MANDATORY' src/main/java/com/spt/learningmanage/service/impl/KnowledgeIndexEventPublisherImpl.java
grep -Fq 'text-embedding-v4' src/main/resources/application.yml
grep -Fq 'qdrant/qdrant:v1.18.2' deploy/docker-compose.yml
grep -Fq 'payload.put("userId"' src/main/java/com/spt/learningmanage/service/impl/KnowledgeDocumentFactoryImpl.java
grep -Fq 'payload.put("sourceVersion"' src/main/java/com/spt/learningmanage/service/impl/KnowledgeIndexServiceImpl.java
grep -Fq 'payload.putIfAbsent("updatedAt"' src/main/java/com/spt/learningmanage/service/impl/KnowledgeIndexServiceImpl.java
grep -Fq 'indexed_at = CURRENT_TIMESTAMP(3)' src/main/java/com/spt/learningmanage/service/impl/KnowledgeIndexServiceImpl.java
grep -Fq 'initialBackfillAndForcedRebuildAreIdempotentAndReconcileExactly' src/test/java/com/spt/learningmanage/integration/KnowledgeIndexEndToEndIT.java
grep -Fq 'visibilityAndMembershipContractionRemoveUnauthorizedReviewPoints' src/test/java/com/spt/learningmanage/integration/KnowledgeIndexEndToEndIT.java
grep -Fq 'transientAndPermanentEmbeddingFailuresAreAuditableAndRecoverable' src/test/java/com/spt/learningmanage/integration/KnowledgeIndexEndToEndIT.java
grep -Fq 'IMPLEMENTATION_COMPLETE / ACCEPTANCE_DEFERRED' docs/stage3/README.md

printf 'stage4.acceptance.contract=PASS\n'
