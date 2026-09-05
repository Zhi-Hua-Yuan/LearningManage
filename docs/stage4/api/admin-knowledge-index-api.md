# Stage 4 admin knowledge-index API

All endpoints require an authenticated `SYSTEM_ADMIN`. Platform administration does not grant access to private project or weekly-review content. Responses contain metadata only and keep the standard `{code,message,data}` envelope.

## Status

```http
GET /api/admin/ai/knowledge/status
```

Returns the worker flag, model/dimension, collection/alias, and counts grouped by event, document, and backfill status.

## Events

```http
GET /api/admin/ai/knowledge/events?status=DEAD&current=1&size=20
POST /api/admin/ai/knowledge/events/{eventId}/replay
```

Only a `DEAD` event can be replayed. Replay uses a conditional update, clears stale claim/failure metadata, creates a new Trace ID, and returns the event to `PENDING`.

## Backfill

```http
POST /api/admin/ai/knowledge/backfills
GET  /api/admin/ai/knowledge/backfills/{runId}
```

```json
{
  "runKey": "stage4-initial-v1",
  "runType": "INITIAL",
  "sourceScope": "ALL",
  "batchSize": 500
}
```

`runKey` is the idempotency key. `batchSize` is persisted and must be 100-1000. A backfill moves through `PENDING -> RUNNING -> ENQUEUED -> SUCCEEDED/PARTIAL`; ENQUEUED is not terminal and waits for every child event to reach SUCCESS or DEAD.

## Non-public operations

Collection destruction, full rebuild into a new collection, and alias switching are intentionally absent from the HTTP API. They use the release runbook and require a separately authorized maintenance session.
