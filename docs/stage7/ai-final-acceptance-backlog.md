# Stage 7 final AI acceptance backlog

Status: **PLANNED**

This backlog receives the protected real-model and human-review work deferred by `../stage3/release/deferred-acceptance.md`. It must be updated with the implemented Stage 4-6 API, schema, dataset, and security contracts before execution.

## Entry criteria

- The five existing AI scenes are stable and their current Prompt versions are inventoried.
- RAG indexing and query flows are implemented behind their intended feature controls.
- Controlled Agent scenarios and registered read-only tools are implemented.
- Backend/frontend candidate SHAs, model configuration, prices, datasets, fixtures, and manifests can be frozen.
- All ordinary deterministic, authorization, migration, contract, and integration tests pass.

## Workstream A: existing AI scenes

- Revalidate all five scenes and six Prompt codes through the public API and `AiInvocationPipeline`.
- Run one development qualification, three regression rounds, and three holdout rounds with cache and sharing disabled.
- Enforce structure, business validation, resource-ID, degradation, Trace, Usage, latency, Token, cost, and no-formal-write thresholds.
- Compare the then-current Prompt against the last valid baseline; do not assume Prompt v2 is still current.

## Workstream B: RAG

- Use at least 30 labeled retrieval questions and report Recall@5.
- Verify citation existence, accessibility, relevance, and consistency with the answer context.
- Verify low-evidence abstention instead of unsupported conclusions.
- Require zero cross-user and cross-team retrievals.
- Verify deleted, stale-version, or permission-revoked sources cannot be cited.
- Verify indexing idempotency, retry behavior, eventual deletion, and rebuildability from MySQL.

Initial target retained from the project plan: Recall@5 at least 80%, with every permission and citation-safety invariant passing independently of averages.

## Workstream C: controlled Agent

- Evaluate project-risk and team-workload scenarios against complete synthetic facts.
- Verify only registered tools can run and every tool repeats permission checks.
- Verify typed argument validation, maximum tool-call count, single-tool timeout, overall timeout, duplicate-call handling, and partial failure.
- Verify prompt injection cannot select an unregistered or write-capable tool.
- Require zero direct project/task mutations and complete Agent-run/tool-call audit records.
- Verify draft confirmation remains explicit and idempotent.

## Workstream D: integrated demonstration

Exercise the repeatable product path:

```text
goal input
-> AI task-breakdown draft
-> user confirmation
-> task and weekly-review history
-> permission-filtered RAG answer with citations
-> controlled Agent risk analysis
-> user-confirmed analysis draft
```

Verify both successful execution and provider/RAG/tool partial-failure paths.

## Human review and final evidence

- Randomly sample 20% of semantic results across existing AI, RAG, and Agent outputs.
- Record reviewer, date, rubric version, per-dimension scores, and disagreement reasons.
- Require at least 80% human/grader agreement; revise the rubric and rerun if it fails.
- Generate sanitized reports, failure case IDs, Prompt/dataset manifests, exact SHA bindings, cost/Token/latency reports, and a SHA-256 evidence index.
- Keep raw questions and model outputs only in restricted expiring artifacts.
- Record final acceptance in the repository. A GitHub Release and annotated tag are not required unless separately requested.

## Exit criteria

- Existing AI, RAG, Agent, and end-to-end deterministic safety gates all pass.
- Required semantic, retrieval, latency, Token, cost, and agreement thresholds pass against frozen evidence.
- Known limitations and any accepted residual risks are documented without presenting unverified metrics as facts.
- `docs/stage3/reports/current-baseline.md` is replaced or supplemented by the final sanitized baseline.
