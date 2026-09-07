# Stage 7 local verification — 2026-09-07

Status: `LOCAL_ACCEPTANCE_PASS / PROTECTED_RELEASE_GATES_PENDING`

All runtime checks used disposable Stage 7 databases, containers and volumes.
No production data or production credential was used.

## Build and automated tests

- JDK 17 offline Maven package completed.
- Clean non-MySQL `*Test` regression: 791 tests, 0 failures, 0 errors.
- Historical V7 MySQL integration partition: 61 tests, 0 failures, 0 errors.
- V8 cleanup lifecycle integration: 5 tests, 0 failures, 0 errors.
- The V8 integration covers dry-run to formal cleanup, concurrent single-active
  submission, stale execution-token rejection, pending-run cancellation,
  database-side operations aggregation and complete failure pagination.
- Stage 7 static acceptance passed; all six Grafana dashboards parsed and met
  the title, UID and panel contract.
- Frontend: 69 test files and 499 tests passed.
- Frontend lint, storage, task-cache and AI rendering safety gates passed.
- Frontend type-check and production build passed.
- Frontend API contract tests passed with 60 operations.

## Database migration and recovery evidence

- Empty isolated MySQL 8.0.41 installation executed V1→V8 successfully.
- Restored V7-shaped database executed V7→V8 successfully.
- Both paths produced 39 tables and a successful Flyway V8 history row.
- The application database account remained DML-only after migration.
- A partial V8 schema was rejected by the migration preflight guard and left no
  additional V8 artifacts behind.
- V8 SHA-256 is
  `9839E9E015A1B7D8C97F68F9A64CEEA52A323453071193E2CB6B39A4D61CE3A4`.

## Docker observability and runtime evidence

- The isolated stack started MySQL 8.0.41, Redis 7.4, Qdrant 1.18.2, the AI
  stub, backend, Prometheus 2.54.1, Grafana 11.2.2 and Tempo 2.6.1.
- `/api/health`, liveness and core readiness returned success when MySQL was
  healthy; the management port had no host mapping.
- Runtime OpenAPI exposed 89 operations. All 60 frontend operations matched;
  missing operations: 0.
- With RAG, Agent, Agent Worker and Knowledge Worker explicitly enabled, the
  deterministic runtime smoke created indexed task history, returned a RAG
  answer with a source, completed a project-risk Agent Run with a draft, and
  verified every emitted application metric label against the allow-list.
- Prometheus had 1/1 scrape target UP and loaded all 10 alert rules.
- Promtool configuration and alert rule tests passed, including controlled fire
  and recovery cases.
- All six Grafana dashboards were provisioned.
- Tempo was ready and returned 20 trace records from the validation run.
- Redis anonymous access returned `NOAUTH Authentication required`; the
  `learning_app` ACL user returned `PONG`.
- Every runtime image used by the Release Gate is pinned by version and
  repository SHA-256 digest.

## Controlled failure drills

- MySQL stopped: core readiness returned HTTP 503 with `DOWN` in 5.29 seconds;
  after restart it returned HTTP 200 with `UP`.
- Redis stopped: core readiness stayed UP, AI dependency health became
  DEGRADED, and recovered after Redis restart.
- Qdrant stopped with the knowledge worker enabled: core readiness stayed UP,
  AI dependency health became DEGRADED, and recovered after Qdrant restart.
- Prometheus and Tempo stopped: the compatible business health endpoint and
  core readiness stayed UP; both observability services recovered cleanly.
- Cleanup concurrency testing found and fixed a repeatable-read race by adding
  current-read locking behind the singleton submission lock.
- MySQL outage testing found and fixed duplicate database health probing and
  restored explicit `DOWN`/`OUT_OF_SERVICE` HTTP 503 mappings.

## Pending protected release evidence

- Controlled real-Qwen call proving provider Token, request ID, metric and Trace
  propagation without telemetry body leakage.
- Remote GitHub Actions execution of the Stage 7 production-operations workflow.
- Immutable backend/frontend candidate SHAs, cross-repository release manifest,
  review closure, annotated tags, GitHub Release and SHA-256 seal.

These remaining items require protected credentials or an explicitly frozen
release candidate. They do not block local development completion, but they do
block promotion to `stage7-v1.0.0`.
