# Stage 5 risk register

| Risk | Control | Residual status |
|---|---|---|
| Cross-user/team retrieval | server-derived Qdrant filter + batch PermissionService + current projection check | Low |
| Stale vector enters prompt | MySQL rebuild + sourceVersion check + corrective Outbox | Low |
| Access changes during generation | final project/source/hash recheck, one full retry | Low |
| Prompt injection in indexed text | untrusted JSON evidence envelope + no tools + citation validation | Medium |
| Forged model citations | server-assigned S IDs and exact marker/declaration validation | Low |
| Raw question/source leaks to call log | keyed HMAC query audit + METADATA_ONLY call logging | Low |
| Rerank outage | explicit vector-order fallback and degraded response | Medium |
| Embedding/Qdrant outage hallucinates answer | fail closed; no Chat call | Low |
| Local MySQL integration unavailable | authoritative isolated MySQL/Qdrant workflow passed remotely | Closed for implementation acceptance |
| Real-provider protocol or cited path differs from deterministic CI | protected real document/query Embedding, qwen3-rerank and cited Chat run passed | Low |
| Broad real-model semantic quality variance | deterministic 50-case retrieval gate plus protected real smoke; broader semantic qualification remains Stage 7 scope | Medium / deferred to Stage 7 |
