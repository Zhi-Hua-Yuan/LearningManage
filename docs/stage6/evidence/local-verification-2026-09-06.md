# Stage 6 local verification — 2026-09-06

Status: `BACKEND_DETERMINISTIC_PASS / EXTERNAL_GATES_PENDING`

## Passed

- JDK 17 backend compile passed after V7 and Agent implementation.
- Stage 6 tests cover configuration bounds, async API contracts, idempotent
  submission, state transitions, Tool registration/strict arguments, Worker
  finalization, Tool-capable pipeline rounds, report privacy shaping and V7
  immutability.
- Spring application context passed with Agent disabled by default.
- Promptfoo Stage 6 contract tests passed and `promptfooconfig.yaml` validated.
- MySQL 8.0.41 empty installation applied V1-V7, Flyway Validate passed and all
  eight V7 post-verification checks passed.
- A V6 database containing user/team/project fixtures upgraded with exactly one
  V7 migration; all fixture rows remained and new data versions started at zero.
- `AgentEndToEndIT` passed four application-path scenarios covering both Agent
  scenes, Run/draft idempotency, pending/running cancellation, worker fencing,
  stale-draft rejection and member report privacy.
- Promptfoo application-path evaluation passed `100/100`: 30 project-risk, 30
  team-workload, 20 unregistered-Tool injection and 20 controlled-failure cases.
- Deterministic suite excluding explicitly named MySQL/real-provider tests:
  `778 tests, 0 failures, 0 errors, 13 skipped`.
- A full unfiltered run reached `811 tests, 0 assertion failures`; its 60 errors
  were connection setup errors because the protected `TEST_DB_USERNAME` and
  `TEST_DB_PASSWORD` were not available in this local shell.

## Pending external evidence

- Real Qwen Tool Calling application-path validation is defined in
  `.github/workflows/stage6-real-agent.yml`; deterministic 100-case validation
  is complete and the protected workflow is ready to dispatch.
- The companion frontend implements Agent submission/polling/cancellation,
  draft confirmation, report list/detail, privacy-safe rendering and deletion.
  Its 491 tests, lint gates, 54-operation contract and production build pass.
- Exact-SHA cross-repository runtime OpenAPI comparison remains a CI/release gate.
- Local runtime OpenAPI comparison passed: frontend `54`, runtime `80`, matched
  `54`, missing `0`.
- The GitHub CLI is authenticated and the `real-ai-validation` environment
  exposes the required secret to its protected workflow. Real-provider status
  remains pending until that exact-SHA workflow completes.

No Stage 6 release or completion claim is made by this local record.
