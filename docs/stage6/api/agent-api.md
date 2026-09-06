# Stage 6 Agent API contract

## Submit project risk

```http
POST /api/ai/agent/project-risk
{"projectId":1001,"clientRequestId":"client-generated-id"}
```

## Submit team workload

```http
POST /api/ai/agent/team-workload
{"teamId":2001,"clientRequestId":"client-generated-id"}
```

Both return `{"runId":"opaque UUID","status":"PENDING"}`. The tuple
`userId + scene + clientRequestId` is idempotent.

## Run lifecycle

```text
PENDING / RUNNING / SUCCEEDED / PARTIAL / FAILED / TIMED_OUT / CANCELED
```

`GET /api/ai/agent/run/{runId}` is owner-only. Cancellation immediately closes
a PENDING run and cooperatively stops a RUNNING run after the current read-only
operation. Only SUCCEEDED/PARTIAL runs expose a draft ID.

## Confirm and read reports

```http
POST /api/ai/agent/report/confirm
{"draftId":"draft-id","operationId":"client-id"}
```

Confirmation rechecks Run ownership/status, target permission, data version and
stored citation hashes. Report list/detail responses are permission-shaped:
team OWNER/ADMIN receive manager data; MEMBER receives only public summary and
their own metrics.

