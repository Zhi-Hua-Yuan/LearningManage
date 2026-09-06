# V5 data dictionary

V5 creates three append-only RAG tables and does not alter V1-V4 tables.

## ai_rag_query_log

Metadata-only audit for one request: requester/project, keyed question HMAC, execution status, model/config versions, thresholds, candidate counts, degradation, failure type, duration, and trace ID. It never stores the raw question.

## ai_rag_result

User-owned answer with lifecycle `ACTIVE / STALE / INVALIDATED / EXPIRED`, insufficient-evidence and degradation flags, Chat call/prompt/model metadata, knowledge timestamp, trace ID, and expiration. Answer bodies default to 30-day retention.

## ai_rag_result_source

Answer-local citation ID plus source/document/chunk identity, content and payload hashes, retrieval scores, title snapshot, and source update time. It never copies source body text.

Key constraints:

```text
request_id unique
query_log_id unique in result
result_id + citation_id unique
result_id + document_key + chunk_index unique
source_type limited to TASK/WEEKLY_REVIEW
```
