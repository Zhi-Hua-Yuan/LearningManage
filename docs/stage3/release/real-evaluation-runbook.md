# Real evaluation and evidence-sealing runbook

Status: **DEFERRED TO STAGE 7**

Do not run this procedure as a condition for starting Stage 4. The deferral decision and frozen handoff are recorded in `deferred-acceptance.md`. When Stage 7 reopens acceptance, first inventory all Prompt, dataset, fixture, model, RAG, and Agent changes and regenerate their manifests; do not assume the Stage 3 hashes are still current.

1. Select an immutable backend SHA and its paired frontend SHA.
2. Run `Stage 3 Real Model Evaluation` through the protected `stage3-real-ai` GitHub Environment.
   Configure the existing `AI_PRICE_VERSION`, `AI_PRICE_CURRENCY=CNY`, `QWEN_PLUS_INPUT_PRICE`, and `QWEN_PLUS_OUTPUT_PRICE` variables, plus `STAGE3_GRADER_PRICE_VERSION`, `STAGE3_GRADER_INPUT_PRICE_CNY_PER_MILLION`, and `STAGE3_GRADER_OUTPUT_PRICE_CNY_PER_MILLION`. The grader output is capped at 1024 tokens, and every retry without Usage adds a conservative maximum-attempt reserve to the candidate cost. The workflow rejects missing or malformed prices instead of reporting a false zero cost.
3. Confirm that the target database is temporary, loopback-only, non-3306, and ends in `_eval`.
4. Run regression and holdout splits three times each with cache and sharing disabled.
5. Use `qwen-plus` at temperature 0 and a different grader model (default `qwen-max`). If the grader is unavailable, stop semantic automation and attach an approved manual score sheet.
6. Fill `human-review-sample.json`, then run `npm run human:verify -- human-review-sample.json human-review-result.json`. Agreement below 80% invalidates the semantic gate and requires a rubric revision and rerun.
7. Verify every deterministic safety metric in every round; averages may not hide a single permission or ID leak.
8. Compare P95, Token, and cost with the frozen baseline using the configured regression ratios.
   For a Prompt candidate, pass the repository-relative frozen aggregate through the workflow's `baseline_summary_path` input. If price versions differ, the gate keeps the Token comparison authoritative and requires the release report to regenerate the monetary baseline with the new configured prices.
9. If a Prompt candidate passes, enable exactly one version per Prompt Code in a transaction. Roll back by re-enabling the last sealed version.
10. Generate the evidence index with `STAGE3_EVIDENCE_INDEX_OUTPUT=... bash scripts/ci/create-stage3-evidence-index.sh`.
11. Generate and validate the candidate manifest with `create-stage3-release-manifest.sh` and `verify-stage3-release-manifest.sh`. These scripts refuse a candidate without six passing real rounds and a passing human-review result.
12. Commit the sanitized final result, manifest, and SHA-256 evidence index. Do not create an annotated tag or publish a GitHub Release unless a separate future decision explicitly requests it.

Until this procedure passes, keep Stage 3 marked `IMPLEMENTATION_COMPLETE / ACCEPTANCE_DEFERRED`; do not present Prompt v2 as having passed real-model qualification. Raw model output remains an expiring restricted Artifact even when no GitHub Release is published.
