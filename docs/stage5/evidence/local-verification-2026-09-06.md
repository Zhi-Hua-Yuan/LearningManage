# Stage 5 local verification — 2026-09-06

Status: `LOCAL_PASS / REMOTE_GATES_PENDING`

## Backend

- JDK 17 production and test compilation: PASS.
- Stage 5 migration/client/retrieval/permission/citation/lifecycle/controller tests: PASS.
- All tests not requiring local MySQL or protected cloud credentials: PASS.
- Candidate JAR package with tests skipped: PASS.
- The candidate CI contract contains 800 deterministic tests; protected real-dependency tests run in their dedicated workflow.
- Local full MySQL run is unavailable because protected `TEST_DB_USERNAME/TEST_DB_PASSWORD` are intentionally not injected into this desktop session. The isolated GitHub workflow is authoritative for those tests.

## Frontend

```text
Vitest: 65 files, 487 tests, PASS
vue-tsc: PASS
lint:ci: PASS
AI rendering safety: PASS
API contract: 46 operations, PASS
Vite production build: PASS (774 modules)
```

The RAG answer and source titles are rendered through `SafeAiText`; hostile HTML remains inert text.

## Evaluation

```text
Promptfoo configuration validation: PASS
Dataset contract: 50 cases
Regression: 30
Holdout: 20
```

The application-path evaluation itself requires the isolated Stage 5 workflow because it starts MySQL, Qdrant, the application, indexing workers, and deterministic providers.
