# Stage 7: Production operations, observability and data lifecycle

Status: `IMPLEMENTATION_COMPLETE / LOCAL_ACCEPTANCE_PASS / RELEASE_PENDING`

Stage 7 starts from the released `stage6-v1.0.0` candidate and adds no new AI
business scene. Its boundary is operational safety: private management-plane
health, low-cardinality metrics, OTLP tracing, sanitized SYSTEM_ADMIN views,
resumable retention jobs and reproducible Docker observability. Cleanup
submission is serialized by a singleton database row before active-run checks.

## Frozen starting point

| Item | Baseline |
|---|---|
| Backend | `70421b6d5fe90b9cba3228d4f639f46b959332ab` |
| Frontend | `e43c3701d23cfe4edc947f9e4cdd528a5125688d` |
| Tag | `stage6-v1.0.0` |
| Database | V7 |
| Backend tests | 828 |
| Frontend tests | 493 |
| Agent eval | 100/100 |
| Frontend/runtime API | 54/54 |

## Runtime boundary

```text
business/RAG/Agent/worker execution
-> low-cardinality Micrometer metrics + application trace correlation
-> private Actuator management port
-> Prometheus / Grafana / Tempo

retention request
-> SYSTEM_ADMIN authorization
-> durable cleanup Run and per-resource Items
-> approved dry-run binding
-> leased cursor-based worker
-> redaction before metadata deletion
-> sanitized admin audit
```

## Safety invariants

- V1-V7 stay immutable; Stage 7 schema is V8.
- `/api/health` stays compatible; the private management plane uses port 9123.
- Metrics never use actor, target, request, run or trace IDs as labels.
- Cleanup is disabled by default and a matching successful dry-run is required.
- RUNNING AI/RAG/Agent records and DEAD knowledge events are never auto-deleted.
- Confirmed reports are retained until user deletion; deleted report bodies wait 30 days.
- Redis, Qdrant, model and telemetry failures do not change core readiness.

See [observability architecture](architecture/ADR-001-observability-boundary.md),
[retention runbook](operations/data-retention-runbook.md), and the
[acceptance contract](acceptance/stage7-acceptance-contract.json). Final
promotion follows the [release runbook](release/stage7-runbook.md).
Metric source, grain and freshness definitions are frozen in the
[metric catalog](observability/metric-catalog.md). Local Docker, migration,
failure-drill and regression results are recorded in the
[local verification evidence](evidence/local-verification-2026-09-07.md).
