# Stage 7 data-retention runbook

## Enablement

1. Apply V8 with the dedicated Flyway account.
2. Deploy with `AI_CLEANUP_ENABLED=false` and verify all migration counts.
3. Enable the worker only: `AI_CLEANUP_ENABLED=true`, schedule still false.
4. Submit a full-resource Dry Run and wait for `SUCCEEDED`.
5. Review every Item cutoff and estimate; compare with direct read-only counts.
6. Submit a formal run with a new `clientRequestId` within 24 hours.
7. Verify redacted/deleted counts and Stage 0-6 smoke tests.
8. Enable `AI_CLEANUP_SCHEDULE_ENABLED=true` only after this rehearsal.

## Defaults

| Resource | Action | Retention |
|---|---|---:|
| AI request/response/error body | redact | 30 days |
| AI call metadata | delete terminal records | 90 days |
| RAG answer body | redact and expire | 30 days |
| RAG query/result/source history | delete | 90 days |
| Agent Run/Tool metadata | delete terminal records | 90 days |
| SUCCESS knowledge events | delete | 14 days |
| DEAD knowledge events | never automatic | permanent until replay |
| Terminal draft payload | redact | 30 days |
| Deleted report content | redact | 30 days after deletion |
| Admin operation audit | delete | 180 days |

## Recovery

Cleanup uses a Run lease and per-resource primary-key cursors. A stopped worker
can be reclaimed after lease expiry. The fencing token prevents an old worker
from writing a terminal state. Formal runs stop before mutation when the
approved Dry Run is missing, expired or outside the configured estimate drift.

Do not manually update cursors or statuses in production. Preserve the run and
item rows as incident evidence and recover by restarting the worker. Restore
business data only from a verified MySQL backup; Qdrant remains rebuildable.
