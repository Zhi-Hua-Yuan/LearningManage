# Stage 6: Controlled asynchronous Agent and analysis reports

Status: `RELEASED / stage6-v1.0.0`

Released backend SHA: `70421b6d5fe90b9cba3228d4f639f46b959332ab`

Released frontend SHA: `e43c3701d23cfe4edc947f9e4cdd528a5125688d`

Stage 6 adds durable asynchronous project-risk and team-workload analysis on top
of the Stage 1 permission boundary, Stage 2 model protocol/draft lifecycle and
Stage 5 permission-aware retrieval.

## Frozen runtime boundary

```text
authenticated submission
-> durable MySQL Agent Run
-> leased Worker with explicit actor context
-> registered read-only Tool
-> structured analysis
-> data-version verification
-> AI draft
-> user confirmation
-> analysis report
```

- Agent workers never use `UserHolder`; actor and target IDs come from the persisted Run.
- Tool arguments cannot select a different project/team.
- Project risk supports Qwen Tool Calling with a fixed-workflow fallback.
- Team workload uses a fixed workflow and separate manager/public prompts.
- No Agent path can directly mutate tasks, projects, reviews or memberships.
- Stage 5 remains `IMPLEMENTATION_COMPLETE / RELEASE_NOT_PLANNED`.

## Public API

```text
POST /api/ai/agent/project-risk
POST /api/ai/agent/team-workload
GET  /api/ai/agent/run/{runId}
POST /api/ai/agent/run/{runId}/cancel
POST /api/ai/agent/report/confirm
GET  /api/ai/report
GET  /api/ai/report/{reportId}
POST /api/ai/report/{reportId}/delete
```

The feature and worker are disabled by default. V7 is forward-only and keeps
all Stage 0-5 APIs unchanged.

## Evaluation

`evals/stage6` contains 30 project-risk, 30 team-workload, 20 unregistered-Tool
injection and 20 controlled-failure application-path cases. The suite submits an asynchronous run, polls to a terminal state and
asserts draft-only success, the four-Tool limit and the frozen orchestration
mode. Run:

```text
npm test --prefix evals/stage6
npm run validate --prefix evals/stage6
npm run eval --prefix evals/stage6
```

The live eval requires `STAGE6_API_BASE_URL`, `STAGE6_EVAL_ACCOUNT`,
`STAGE6_EVAL_PASSWORD`, `STAGE6_PROJECT_ID` and `STAGE6_TEAM_ID`.
