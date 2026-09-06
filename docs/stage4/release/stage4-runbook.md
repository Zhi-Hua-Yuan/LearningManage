# Stage 4 migration, rollout, rebuild, and rollback runbook

## Deployment order

1. Freeze backend SHA and verify all V1-V4 migration hashes.
2. Back up MySQL and verify restore into an isolated database.
3. Run V4 preflight and `flyway validate`.
4. Run `flyway migrate` with the dedicated migrator account.
5. Run `sql/flyway/stage4/post_verify_v4.sql` and confirm four PASS rows.
6. Start Qdrant 1.18.2 with a persistent volume. Bind the final image digest in the release manifest.
7. Start the application with `AI_KNOWLEDGE_WORKER_ENABLED=false`; verify core project/task/review APIs.
8. Initialize/validate `learning_knowledge_v1_1024` and `learning_knowledge_current` through a controlled worker smoke.
9. Verify that the GitHub Environment `real-ai-validation` contains a non-empty
   `ALIYUN_API_KEY` secret. Run the protected 10-document `text-embedding-v4` smoke
   against the exact release SHA and verify model identity, usage, and dense dimension
   1024. Do not use private production text.
10. Enable the worker, submit `stage4-initial-v1`, and wait for the backfill terminal state.
11. Require event backlog zero, DEAD zero, MySQL/document/Qdrant reconciliation difference zero, and freshness P95 <= 60 seconds.
12. Freeze evidence before enabling Stage 5 work.

## Failure rollback

- Disable `AI_KNOWLEDGE_WORKER_ENABLED` first. Core business APIs remain available and continue capturing Outbox events.
- Do not downgrade or edit V4. Apply forward fixes only.
- A bad Embedding/Qdrant release is rolled back by restoring the prior application image and prior stable collection alias.
- Qdrant is never restored into MySQL. If vector state is uncertain, create a new collection, run REBUILD, validate, then atomically switch the alias.
- DEAD events remain auditable. Replay only after the root configuration/provider problem is fixed.

## Collection rebuild

1. Provision `learning_knowledge_v{next}_1024` with Cosine distance and the frozen payload indexes.
2. Deploy a maintenance candidate configured with the new physical collection while keeping the public alias unchanged.
3. Submit a unique REBUILD backfill. REBUILD forces Embedding even when MySQL document hashes already match.
4. Compare active logical documents, chunk counts, payload scopes, and Point counts.
5. Switch `learning_knowledge_current` atomically.
6. Retain the old collection for the rollback window, then remove it only in a separately approved destructive operation.

## Security conditions

- Local Docker binds Qdrant to `127.0.0.1`.
- Non-local/production configuration enables `QDRANT_REQUIRE_SECURE_TRANSPORT=true`, which requires HTTPS and an API key.
- Logs and evidence contain hashes, counts, durations, statuses, and request IDs only; never source text or vectors.
