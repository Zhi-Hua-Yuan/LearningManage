# Stage 5 release runbook

## 1. Freeze candidates

1. Record exact backend and frontend SHAs from the protected Stage 5 branches.
2. Verify both worktrees are clean.
3. Verify the V5 SHA-256 matches both published migration manifests.
4. Do not modify V1-V5 after candidate freeze.

## 2. Required gates

```text
Backend CI: 806/806 tests, V5 empty install, V4->V5 upgrade, Docker runtime
Stage 5 RAG: MySQL + Qdrant + deterministic Query Embedding/Rerank/Chat E2E
Stage 5 Promptfoo: 30 regression + 20 holdout cited Hit@5 cases
Frontend CI: tests, coverage, type-check, lint, AI render safety, build
Cross-repository API contract: 46 frontend operations matched
Protected real RAG: real document/query Embedding, qwen3-rerank and cited Chat path
```

## 3. Deployment order

1. Back up MySQL and validate the restore command.
2. Run `flyway validate`, then migrate V5 with the migration account.
3. Deploy backend with `AI_RAG_ENABLED=false` and knowledge indexing enabled.
4. Complete INITIAL/RECONCILE backfill; verify no unresolved DEAD events.
5. Configure Query Embedding, Rerank, Qdrant and the independent HMAC secret.
6. Run protected smoke and permission-negative checks.
7. Set `AI_RAG_ENABLED=true` and deploy the frontend candidate bound to the backend SHA.
8. Re-run runtime OpenAPI matching and one owner/one outsider smoke.

## 4. Rollback

- Disable `AI_RAG_ENABLED` first. Project/task/review operations remain available.
- Roll back application images without dropping V5 tables.
- Do not use Flyway clean or reverse V5 DDL in an incident.
- Qdrant remains disposable; rebuild from MySQL/Outbox if retrieval state is suspect.
- Preserve query/result metadata for audit, while expired answer bodies follow retention cleanup.

## 5. Seal

After every required gate passes for the exact SHA pair, create the Stage 5 candidate manifest, calculate its SHA-256, create an annotated `stage5-complete-*` tag, and publish a GitHub Release containing the manifest, test summaries, evaluation report, known limitations, and rollback instructions.
