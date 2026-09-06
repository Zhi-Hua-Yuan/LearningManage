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
| Local MySQL integration unavailable | remote isolated MySQL/Qdrant workflow | Open until remote run |
| Real-model quality unknown | 50-case protected three-run evaluation | Open until protected run |
