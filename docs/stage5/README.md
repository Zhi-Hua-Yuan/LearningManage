# Stage 5: Permission-aware RAG

Status: `IMPLEMENTATION_COMPLETE / RELEASE_NOT_PLANNED`

The project owner decided on 2026-09-06 to proceed directly to Stage 6 without
creating a Stage 5 tag or GitHub Release. This is an explicit release decision,
not an unfinished RAG implementation. See
[`release/release-decision-2026-09-06.md`](release/release-decision-2026-09-06.md).

Stage 5 adds single-project, single-turn question answering over the Stage 4 TASK and WEEKLY_REVIEW index. The implementation never trusts vector payloads as business facts: candidates are filtered in Qdrant, batch-authorized through `PermissionService`, rebuilt from current MySQL rows, version-checked, reranked, and cited before an answer can be persisted.

## Frozen flow

```text
project permission
-> query embedding (text_type=query, 1024 dimensions)
-> Qdrant project/visibility filter, Top 20
-> batch source authorization
-> rebuild current chunks from MySQL
-> sourceVersion comparison
-> qwen3-rerank, Top 8
-> untrusted evidence envelope
-> AiInvocationPipeline with metadata-only logging
-> citation validation
-> final permission/hash recheck
-> result + citation transaction
```

## Public API

```text
POST /api/ai/rag/ask
GET  /api/ai/rag/result/{requestId}
```

## Evidence

- Local deterministic Stage 5 tests cover migration, Query Embedding, Qdrant filters, rerank, source reconstruction, batch permission checks, citation validation, lifecycle, controller contract, and log-body suppression.
- Frontend tests, type-check, lint, AI rendering safety, API-contract export, and production build pass in the companion repository.
- `stage5-rag.yml` runs MySQL, Qdrant, deterministic Chat/Embedding/Rerank services, the application-path integration test, and a 50-case Promptfoo Hit@5/citation evaluation.
- Remote MySQL/Qdrant integration, 50/50 application-path evaluation, frontend
  CI, and the protected real-provider RAG path passed. Exact evidence is listed
  in the release decision record.
- No `stage5-complete-*` tag or GitHub Release is planned. The status must not be
  presented as `RELEASED`.

## Boundaries

- No chat history, query rewriting, file ingestion, hybrid keyword search, or Agent behavior.
- Qdrant stores vector and metadata only; prompt text is reconstructed from current MySQL state.
- V6 creates an idempotent `stage5-qdrant-numeric-payload-v1` REBUILD run. RAG
  readiness requires that run to succeed so Qdrant integer permission indexes
  cannot be queried against legacy string-encoded identifiers.
- Raw questions and evidence bodies are absent from `ai_rag_query_log`, `ai_rag_result_source`, and RAG `ai_call_log` bodies.
- Rerank failure may fall back to vector order and must be reported as degraded. Embedding or Qdrant failure cannot generate an answer.
