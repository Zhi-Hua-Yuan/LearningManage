# V4 data dictionary

Status: `FROZEN_FOR_IMPLEMENTATION`

V4 introduces four tables and does not alter existing business columns.

| Table | Purpose | Primary invariants |
|---|---|---|
| `ai_knowledge_index_event` | Durable transaction Outbox | auto-increment order; fenced claim; immutable source identity |
| `ai_knowledge_source_lock` | Cross-worker source serialization | unique source; renewable lease/token |
| `ai_knowledge_document` | Desired/indexed document metadata | unique document key; hashes and chunk count; no body/vector |
| `ai_knowledge_backfill_run` | Resumable keyset backfill | unique run key; persisted batch size; fenced claim; ENQUEUED waits for child-event terminal counts |

Enums are stored as uppercase ASCII values and checked in MySQL. Timestamps use `datetime(3)` for lease accuracy. Event and backfill IDs use auto-increment because they are queue/order records; document IDs use the existing application ID convention.

Required indexes:

```text
event(status, next_attempt_at, id)
event(status, lease_until)
event(source_type, source_id, status)
event(backfill_run_id, status)
document(source_type, source_id)
document(project_id, visibility_type)
document(team_id, visibility_type)
document(owner_user_id, visibility_type)
document(status, update_time)
backfill(status, lease_until)
```

No foreign key points from Outbox/document rows to business rows. Sources may be deleted before asynchronous reconciliation; lifecycle correctness is implemented through source re-read and tombstones.

## Qdrant payload contract

Every point is server-derived and contains:

```text
userId, projectId, sourceType, sourceId, sourceVersion, updatedAt
teamId (TEAM-scoped sources only)
```

`ownerUserId` remains as a compatibility alias. `documentKey`, `visibilityType`,
`chunkIndex`, `contentHash`, `payloadHash`, and `indexedAt` support reconciliation and
operations. `sourceVersion` is the deterministic `contentHash:payloadHash` pair. Timestamp
payload values use RFC 3339 with an explicit `+08:00` offset. Qdrant
payload indexes cover the permission/filter fields plus `sourceVersion` and `updatedAt`.
No point contains account names, email addresses, credentials, source text, or vectors in
its payload.
