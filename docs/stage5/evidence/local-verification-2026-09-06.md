# Stage 5 local verification — 2026-09-06

Status: `LOCAL_AND_REMOTE_IMPLEMENTATION_PASS / RELEASE_NOT_PLANNED`

## Backend

- JDK 17 production and test compilation: PASS.
- Stage 5 migration/client/retrieval/permission/citation/lifecycle/controller tests: PASS.
- All tests not requiring local MySQL or protected cloud credentials: PASS.
- Candidate JAR package with tests skipped: PASS.
- The candidate CI contract contains 801 deterministic tests; protected real-dependency tests run in their dedicated workflow.
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

## Remote implementation evidence

- Backend candidate: `b1b20a818ce629aa02f7ff339153e83eb48681b4`.
- Frontend candidate: `9c60170ea36cf2b4e64278fede544cabe608defb`.
- Backend CI: 801 tests plus V6 empty/existing database and Docker gates passed in
  [run 34028032327](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/34028032327).
- Stage 5 deterministic RAG: 50/50 application-path cases passed in
  [run 34027708375](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/34027708375).
- Frontend CI: 487 tests, type-check, API contract and production build passed in
  [run 34022440726](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/34022440726).
- Protected real document/query Embedding, qwen3-rerank and cited Chat path:
  [run 34028167239](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/34028167239), PASS for the backend candidate.
- Formal cross-repository Release candidate, manifest, tag and GitHub Release:
  not planned by project-owner decision.
