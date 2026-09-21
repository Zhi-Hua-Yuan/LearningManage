# Phase 5 Evaluation Matrix

`matrix.json` is the immutable contract joining the existing Stage 3/5/6 datasets. Run:

```bash
node evals/phase5/scripts/validate-matrix.mjs
```

The command is deterministic and does not call a model. Provider execution remains in the protected Phase 5 workflow; its raw reports must be retained on failure and its sanitized summary must be bound to the candidate SHA.

After the Spring AI cutover, collect seven consecutive daily records using `observation.example.json` as the field contract and run:

```bash
node evals/phase5/scripts/verify-observation.mjs evals/phase5/observation.json
```

The checker enforces zero safety incidents, permission leaks and formal writes, complete telemetry for successful calls, failure-rate regression of at most five percentage points, and P95/cost regression of at most 1.2x the Legacy baseline.
