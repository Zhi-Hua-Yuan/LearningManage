# Stage 7 metric catalog

## Source and freshness

| Surface | Source | Grain | Refresh/window |
|---|---|---|---|
| Application operations page | MySQL audit metadata | one AI call, RAG query, Agent Run or queue event | request-time; default last 24h |
| Grafana technical dashboards | Micrometer scraped by Prometheus | counters, timers, summaries and gauges | scrape every 15s; panel query window shown by Grafana |
| Dependency health | cached probe metadata | one dependency | refreshed every 30s |
| Cleanup details | `ai_data_cleanup_run/item` | one run and one resource per item | durable state, request-time |

MySQL and Prometheus values answer different questions and are deliberately
labelled separately. MySQL is the durable audit source; Prometheus is the
near-real-time operational source. Prometheus loss never changes business facts.

## Definitions

| Metric | Definition | Dimensions |
|---|---|---|
| AI invocations | one terminal AI call-log transition | scene, model, status, failure type, degraded |
| AI duration | wall-clock duration recorded by the invocation pipeline | scene, model, status |
| Tokens | provider-reported total usage | scene, model |
| Estimated cost | configured price version applied to provider-reported usage | scene, model, currency, price version |
| RAG query | one terminal `/rag/ask` execution | status, degraded |
| RAG candidates | authorized final retrieval candidates before answer persistence | status |
| Agent Run | one successfully fenced terminal transition | scene, status, orchestration mode |
| Tool call | one terminal registered Tool execution | tool name, status |
| Queue depth | current PENDING count from MySQL | none |
| Oldest pending | age of the oldest PENDING row | none |
| Knowledge event | one Worker outcome | source type, status, failure type |
| Cleanup Run | one fenced terminal cleanup execution | status |
| Cleanup rows | rows redacted or deleted by a terminal cleanup | status |

IDs, traces, actors, projects, teams and free text are excluded from metric
dimensions. Trace-level diagnosis uses logs/Tempo with `app.trace_id`, not a
Prometheus label. The exporter-generated histogram bucket label `le` is a
Prometheus protocol detail, not an application-defined dimension.

## Interpretation guardrails

- `INSUFFICIENT` is a valid RAG quality outcome, not an infrastructure failure.
- A disabled capability is reported as `DISABLED`, not `DOWN`.
- Missing provider token usage leaves cost unknown; it is never treated as zero
  in audit reports.
- Grafana P95 uses histogram samples over its selected time range; the admin
  page computes P95 from durable MySQL rows in the requested window.
- Queue gauges retain their last good value when the metadata refresh fails;
  dependency health indicates that freshness problem separately.
