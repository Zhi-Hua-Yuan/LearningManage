# Dataset governance

Dataset version `1.0.0` contains 170 quality cases and 40 fault-injection cases. The authoritative hashes are in `evals/stage3/dataset-manifest.json`.

| Scene | Development | Regression | Holdout | Quality total | Faults |
|---|---:|---:|---:|---:|---:|
| Task breakdown | 36 | 12 | 12 | 60 | 8 |
| Weekly polish | 18 | 6 | 6 | 30 | 8 |
| Today order | 18 | 6 | 6 | 30 | 8 |
| Daily rename | 15 | 5 | 5 | 25 | 8 |
| List replan | 15 | 5 | 5 | 25 | 8 |

`caseId` is immutable and globally unique. A label change increments `labelVersion`; an altered case is never silently substituted under a previous ID. Holdout results are only used for a release candidate. If a holdout result influences a Prompt change, that holdout version is promoted into regression and a new holdout version is created.

Deterministic assertions own structure, IDs, permissions, dates, ranges, degradation, Usage-null semantics, and write safety. Model grading is limited to open-ended quality and is not permitted to override a deterministic safety failure.

Open-ended grading uses `evals/stage3/fixtures/semantic-context.json` as the synthetic fact catalog. The generated grader contract includes the request, project facts, and only the relevant task facts; every rubric dimension must be scored and explained, with the lowest dimension used as the final semantic score. The same facts, rubric dimensions, and sanitized `data` output are included in the deterministic 20% human-review sample.
