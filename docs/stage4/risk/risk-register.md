# Stage 4 risk register

| Risk | Control | Residual status |
|---|---|---|
| Business succeeds but index event is lost | same-transaction Outbox; rollback on event insert failure | Low |
| Old worker overwrites new vector | source lease, fencing token, post-write hash/access check, corrective event | Low |
| Qdrant outage blocks core CRUD | asynchronous worker, retry/circuit breaker, no external call in business transaction | Low |
| Private review leaks into team vector | separate document builders and negative payload/body tests | Low |
| Re-embedding on status-only changes wastes cost | separate content and payload hashes | Low |
| Event backlog grows while worker is disabled | status metric, backfill/status endpoint, bounded consumers | Medium |
| Embedding provider drift | fixed model/dimension contract and protected synthetic smoke | Medium |
| Qdrant is exposed without auth | loopback local binding; TLS/API key validation outside local profile | Low |
| Admin operations expose private text | metadata-only DTOs and SYSTEM_ADMIN gate | Low |
| Stage 3 deferred acceptance is forgotten | retain Stage 3 backlog and include it unchanged in Stage 4 release manifest | Medium |
| GitHub release cannot be created locally | complete local commits/evidence; remote PR/release requires restored GitHub credentials | Open external dependency |
