# Stage 7 AI operations API

All endpoints use the existing `BaseResponse<T>` envelope and require a logged-in
`SYSTEM_ADMIN`. Time ranges default to the last 24 hours and cannot exceed 90 days.

```text
GET  /api/admin/ai/ops/overview
GET  /api/admin/ai/ops/rag
GET  /api/admin/ai/ops/agent
GET  /api/admin/ai/ops/failures
GET  /api/admin/ai/ops/dependencies
GET  /api/admin/ai/ops/cleanup-runs
GET  /api/admin/ai/ops/cleanup-runs/{runId}
POST /api/admin/ai/ops/cleanup-runs
POST /api/admin/ai/ops/cleanup-runs/{runId}/cancel
```

Operations summaries contain only counts, durations, tokens, estimated cost,
queue depth, normalized failure types and application trace IDs. They never
return prompts, questions, Tool arguments/results, task/review/report bodies or
provider headers.

Cleanup request:

```json
{
  "dryRun": true,
  "resourceTypes": ["AI_CALL_BODY", "RAG_RESULT_BODY", "DRAFT_PAYLOAD"],
  "clientRequestId": "cleanup-request-20260907"
}
```

Omitting `resourceTypes` selects all nine frozen resources. A formal run must
use a new client request ID, provide that reviewed Run's `approvedDryRunId`, and
exactly match a successful, unexpired, unused Dry Run. The database persists
that approval by ID and resource hash. Duplicate requests from the same
administrator return the original Run.

```json
{
  "dryRun": false,
  "resourceTypes": ["AI_CALL_BODY", "RAG_RESULT_BODY", "DRAFT_PAYLOAD"],
  "approvedDryRunId": "cleanup_1234567890abcdef",
  "clientRequestId": "cleanup-formal-20260907"
}
```
