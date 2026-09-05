# Stage 4 requirements contract

Status: `FROZEN_FOR_IMPLEMENTATION`

## Functional requirements

1. Every committed task or weekly-review mutation that can change semantic content, metadata, existence, project scope, or visibility records a durable Outbox event in the same transaction.
2. Outbox failure rolls back the business mutation. Embedding or Qdrant failure never rolls back an already committed business mutation.
3. Workers reconcile current MySQL state rather than replaying stale event bodies.
4. The same source is serialized across workers by a renewable database lease; all event/document terminal writes use fencing tokens.
5. Task and weekly-review documents use deterministic keys, chunks, hashes, and Qdrant point IDs.
6. Content-only changes call Embedding; payload-only changes update payload without recomputing vectors; no-op events perform neither external write.
7. Deleted, inaccessible, moved, or privatized sources remove obsolete points and document rows transition to an auditable tombstone state.
8. Backfill uses keyset pagination and creates Outbox events; it never bypasses the normal reconciliation worker.
9. SYSTEM_ADMIN may inspect sanitized state, start idempotent backfills, and replay DEAD events. This role does not gain business-resource read access.
10. Qdrant can be destroyed and rebuilt from MySQL without losing business facts.

## Security and privacy requirements

- Embedding inputs exclude accounts, email, credentials, user IDs, and assignee names.
- TEAM weekly-review documents contain only week metadata and `sharedSummary`.
- Logs and admin APIs never return source text, vectors, credentials, or unredacted provider errors.
- Qdrant is bound to loopback in local development; non-local deployments require TLS and an API key.
- Payload scopes are server-derived and cannot be supplied by clients or models.

## Compatibility requirements

- V1, V2, and V3 remain immutable.
- Existing business API request and response contracts do not change.
- The application starts normally with the index worker disabled and without Qdrant.
- Existing Stage 0-3 CI and Stage 3 offline evaluation remain mandatory.

## Acceptance thresholds

```text
business/outbox atomicity: 100%
duplicate Qdrant points after replay: 0
old-worker overwrite of newer state: 0
private review fields in TEAM points: 0
business rollback caused by Embedding/Qdrant outage: 0
healthy-dependency index freshness P95: <= 60 seconds
backfill reconciliation difference: 0
real provider smoke vector dimension: 1024
```
