# Stage 5 release decision — 2026-09-06

## Decision

Status: `IMPLEMENTATION_COMPLETE / RELEASE_NOT_PLANNED`

The project owner decided to proceed directly to Stage 6. Stage 5 will not
create an annotated tag, candidate Release Manifest, or GitHub Release.

This decision changes release packaging only. It does not defer the implemented
permission-aware RAG capability or its dedicated validation.

## Frozen implementation references

- Backend: `b1b20a818ce629aa02f7ff339153e83eb48681b4`
- Frontend: `9c60170ea36cf2b4e64278fede544cabe608defb`
- Backend implementation PR: [#142](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/142)
- Frontend implementation PR: [#56](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/56)

## Evidence retained

- Backend CI: 801 deterministic tests, V6 empty installation, existing-database
  upgrade, migration immutability and Docker runtime gates passed in
  [run 34028032327](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/34028032327).
- Stage 4 MySQL/Outbox/Qdrant regression passed.
- Stage 5 MySQL/Qdrant integration and 50/50 Promptfoo application-path
  retrieval/citation evaluation passed in
  [run 34027708375](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/34027708375).
- Frontend CI passed 487 tests, type-check, safe AI rendering, 46-operation API
  contract checks and the production build in
  [run 34022440726](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/34022440726).
- Protected real-provider validation passed document and query
  `text-embedding-v4`, `qwen3-rerank`, Qdrant retrieval, `qwen-plus` cited answer,
  and result lifecycle on
  [run 34028167239](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/34028167239).

## Explicitly not claimed

- Stage 5 is not tagged or released.
- No `stage5-complete-*` tag exists by this decision.
- No final cross-repository Release candidate Manifest is claimed.
- A failed generic cross-repository release attempt is not treated as RAG
  evidence. Its remaining issue concerns the historical AI task-breakdown
  release fixture, not Stage 5 RAG runtime behavior.

## Stage 6 entry

Stage 6 Agent development may start from the backend and frontend references
above. Agent changes must preserve Stage 5 permission filtering, secondary
authorization, source reconstruction, citation validation, metadata-only
logging, feature switches, and no-direct-write boundaries.

If a Stage 5 release is requested later, create a new exact SHA pair and rerun
the complete release gate before producing any tag or Release.
