# ADR-001: Stage 7 observability boundary

## Decision

- Preserve `X-Trace-Id` as the durable application correlation ID.
- Export OpenTelemetry spans separately and attach `app.trace_id` for correlation.
- Bind Actuator to private port 9123; expose only health and Prometheus.
- Use Prometheus for time-series metrics, Tempo for traces and MySQL only for
  durable business/audit metadata.
- Keep operational metric labels on the documented low-cardinality allow-list.
- Expose sanitized business aggregates through SYSTEM_ADMIN APIs; do not proxy
  Prometheus queries through the application.

## Failure boundary

MySQL is part of core readiness. Redis, Qdrant, Chat, Embedding, Rerank,
Prometheus and Tempo are optional capability dependencies. Their failure marks
the AI capability degraded or down without declaring project/task APIs healthy
when MySQL itself is unavailable.

## Sensitive-data boundary

Prompts, questions, task/review/report bodies, Tool arguments/results, provider
headers and stack traces are forbidden from metrics, traces and operations API
responses. IDs with unbounded cardinality are forbidden from metric labels.
