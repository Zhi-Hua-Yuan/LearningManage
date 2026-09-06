# Stage 5: Permission-aware RAG

Status: `IMPLEMENTATION_COMPLETE / REMOTE_ACCEPTANCE_PENDING`

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
- Remote workflow output, real-provider evidence, exact SHAs, tag, and Release remain required before this status may become `PASS / RELEASED`.

## Boundaries

- No chat history, query rewriting, file ingestion, hybrid keyword search, or Agent behavior.
- Qdrant stores vector and metadata only; prompt text is reconstructed from current MySQL state.
- Raw questions and evidence bodies are absent from `ai_rag_query_log`, `ai_rag_result_source`, and RAG `ai_call_log` bodies.
- Rerank failure may fall back to vector order and must be reported as degraded. Embedding or Qdrant failure cannot generate an answer.
