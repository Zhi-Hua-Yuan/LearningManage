# Stage 4: Transactional Outbox and knowledge indexing

Status: `IMPLEMENTATION_COMPLETE / PROTECTED_REAL_EMBEDDING_PENDING`

Stage 4 builds a permission-aware, rebuildable knowledge-index foundation for tasks and weekly reviews. It does not expose RAG question answering or Agent capabilities.

## Target flow

```text
business mutation
-> MySQL business rows + outbox event in one transaction
-> leased worker re-reads current MySQL truth
-> canonical document + deterministic chunks
-> text-embedding-v4 (1024 dimensions)
-> idempotent Qdrant upsert/delete
-> persisted reconciliation state and sanitized audit metadata
```

## Frozen boundaries

- MySQL is the only source of business truth; Qdrant is disposable derived state.
- Outbox capture remains active after V4. `worker-enabled=false` pauses consumption only.
- Indexed sources are `TASK` and `WEEKLY_REVIEW` only.
- Team review vectors contain `sharedSummary` only. Private review text never enters a TEAM document.
- Stage 4 implements document embeddings only. Query embeddings, retrieval, reranking, citations, and RAG APIs belong to Stage 5.
- Stage 3 remains `IMPLEMENTATION_COMPLETE / ACCEPTANCE_DEFERRED`; Stage 4 must keep its offline deterministic gates but cannot claim final real-model quality acceptance.

## Work packages

1. Design, data, security, and acceptance contracts.
2. Immutable V4 migration and migration gates.
3. Embedding and Qdrant clients, configuration, Docker, and deterministic stubs.
4. Transactional Outbox coverage for every task/review/access mutation.
5. Canonicalization, chunking, hashing, leases, source serialization, and reconciliation worker.
6. Idempotent backfill and SYSTEM_ADMIN operational APIs.
7. Integration, concurrency, failure, scale, CI, evidence, and release closure.

## Completion definition

Stage 4 is complete only when incremental capture, initial backfill, deletion/access reconciliation, concurrent idempotency, dependency-failure isolation, Qdrant rebuild, protected provider smoke, and evidence sealing all pass the machine acceptance contract.

Implementation, PR integration, MySQL migration gates, Qdrant integration, Docker runtime, and post-merge regression are complete. Protected workflow run `34006943574` was dispatched for the final evidence SHA but stopped because the `real-ai-validation` Environment has no `ALIYUN_API_KEY`. The protected real Embedding smoke and final release evidence therefore remain pending. No real-model quality or provider-smoke PASS is claimed.

The final completion audit additionally enforces the original Qdrant payload names,
database-clock freshness, idempotent INITIAL/REBUILD reconciliation, visibility and
membership contraction, and retry/DEAD/replay behavior in the dedicated Stage 4
integration workflow.
