# Stage 3: AI evaluation and Prompt regression

Stage 3 turns the five existing AI scenes into reproducible, comparable release gates. It adds no business API, UI, runtime evaluation table, RAG, vector store, or Agent capability. The Flyway head remains V3.

Current status: **IMPLEMENTATION_COMPLETE / ACCEPTANCE_DEFERRED**. PR `#134` is merged at backend SHA `5a98b4b9b39fef1303c0d68b653dbf2d3c129a7f`. Protected real-model qualification, human review, and evidence sealing are deferred to Stage 7 so Stage 4 RAG development can proceed. This status is not a claim that Prompt v2 passed real-model acceptance. See `release/deferred-acceptance.md`.

## Implemented scope

- A locked Node 22.13.1 evaluation project under `evals/stage3`.
- 170 synthetic quality cases and 40 deterministic fault-injection cases.
- A custom Promptfoo provider that calls the existing public API and then correlates `ai_call_log` by trace ID.
- Deterministic structure, resource-ID, permission, range, degradation, usage, and no-formal-write assertions.
- Optional semantic grading with a model that must differ from the system-under-test model.
- Sanitized JSON/Markdown reporting, threshold comparison, prompt and dataset manifests.
- Six-round candidate aggregation plus human-review, evidence-index, and release-manifest sealing contracts.
- An isolated `_eval` database, fixed high-ID fixtures, and a five-scene OpenAI-compatible CI stub.
- Ordinary offline CI and a protected manual real-model workflow.

## Local contract checks

```bash
cd evals/stage3
npm run install:ci
npm run generate:datasets
npm run datasets:manifest
npm run prompt:manifest
npm test
npm run validate
```

The complete application-path evaluation requires Docker and the environment contract documented in `release/real-evaluation-runbook.md`. Raw `output*.json` files are ignored and must only be retained as expiring CI artifacts.

## Current completion state

The implementation, deterministic contract suite, full 714-test backend regression, and a 74/74 local Docker application-path run are complete. The protected real-model development/regression/holdout matrix and 20% human review have not been completed for Prompt v2 and are intentionally deferred to Stage 7.

Development may proceed to Stage 4, but Stage 3 must remain marked `ACCEPTANCE_DEFERRED`. No `stage3-v1.0.0` tag or GitHub Release will be published now. The acceptance thresholds remain frozen and must be applied when Stage 7 reopens the evaluation.

## Directory map

- `requirements/`: frozen boundaries, checksums, and machine acceptance contract.
- `architecture/`: evaluation flow and trust boundaries.
- `datasets/`: dataset design and holdout policy.
- `reports/`: report templates and non-fabricated current status.
- `risk/`: operational and evaluation risks.
- `release/`: deferred-acceptance decision plus the protected real-run, rollback, and sealing procedure.
- `evidence/`: evidence retention and binding rules.
