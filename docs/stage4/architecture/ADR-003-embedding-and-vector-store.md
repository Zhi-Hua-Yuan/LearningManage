# ADR-003: Embedding and vector-store boundaries

Status: `ACCEPTED`

## Decision

- Model: `text-embedding-v4`.
- Dense dimension: `1024`.
- Index input type: `document`.
- Maximum request batch: 10 inputs, grouped within one source/owner boundary.
- Qdrant protocol: REST through Spring `RestClient`.
- Qdrant collection: `learning_knowledge_v1_1024`.
- Stable alias: `learning_knowledge_current`.
- Distance: Cosine.

`EmbeddingClient` and `VectorStoreClient` isolate vendor protocols. Embedding reuses Stage 2 timeout, circuit-breaker, sanitization, usage, cost, and trace conventions, but does not pass through chat-response parsing.

Qdrant point IDs are UUIDv5 values derived from `documentKey + ':' + chunkIndex`. Upsert replaces the same point. When chunk count shrinks, current chunks are upserted before obsolete IDs are deleted.

Model/dimension changes require a new physical collection, full rebuild, validation, and atomic alias switch. Existing vectors are never mutated in place across incompatible dimensions.
