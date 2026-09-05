# Current Stage 3 model baseline

Status: **PROMPT V2 REAL-MODEL ACCEPTANCE DEFERRED TO STAGE 7**

The repository contains the evaluation implementation and deterministic data contracts. Prompt v2 was merged through PR `#134` at backend SHA `5a98b4b9b39fef1303c0d68b653dbf2d3c129a7f`, but no `qwen-plus` quality percentage, semantic score, real latency, Token baseline, cost baseline, or human/grader agreement number is claimed for that candidate.

The protected real-model matrix and human review are intentionally deferred under `../release/deferred-acceptance.md`. The Stage 7 operator must replace this status with the sanitized aggregate produced by `.github/workflows/stage3-real-eval.yml`; raw Promptfoo output remains an expiring artifact and is not committed.

No Stage 3 GitHub Release or annotated release tag is required or authorized by the current decision.

## Existing diagnostic evidence

Protected run `33965002036` exercised the pre-candidate baseline and stopped after the first regression round. It recorded 100% structure parsing, semantic score `0.8694`, deterministic pass rate `73.53%`, business validation rate `75.76%`, Trace/Usage coverage `97.06%`, P95 latency `22,960 ms`, and zero formal business writes.

These values are retained as failure diagnostics only. They are neither a passing Stage 3 baseline nor evidence that the merged Prompt v2 candidate meets the acceptance contract.

## Deterministic implementation verification

- Dataset contract: 170 quality cases + 40 failure-injection cases, all schema-valid and globally unique.
- Promptfoo project contract tests: 22/22 passed, including response-body retries, retry cost reserves, development-round cost, and direct candidate Prompt Manifest binding.
- Promptfoo configuration validation: passed with sharing and cache disabled by the run wrapper.
- Stage 3 repository acceptance: passed; V1/V2/V3 checksums unchanged and no V4 exists.
- Full backend regression: 714/714 tests passed against a disposable, Flyway-managed `_ci_`/`_eval` MySQL database.
- Scene-aware protocol Stub: Python syntax and a detailed task-breakdown response/Usage smoke test passed.
- Production-path offline evaluation: 74/74 regression and fault-injection cases passed through the public API, `AiInvocationPipeline`, scene parsing, business validation, permission checks, and correlated `ai_call_log` metadata.
- Offline deterministic metrics: structure parsing 100%, business validation 100%, expected degradation 100%, Trace-to-`ai_call_log` correlation 100%, formal project/milestone/task content mutations 0, P50 8 ms, and P95 5012 ms. The P95 includes deliberate five-second timeout injections.
- Offline dataset contract: version `1.1.0`, with both task-breakdown Prompt modes represented in development, regression, and holdout.
- Offline dataset digest: `025AFAC654E2EDD10B57EEE3336742AEA9C0F765A06C42A2EBE13D7C0C0F201C`.

These figures describe the deterministic `ci-ai-stub` baseline only. They are not evidence of `qwen-plus` semantic quality, real-provider latency, Token use, or cost. The protected real-model workflow and human-review gate must still pass in Stage 7 before Stage 3 can be described as fully accepted.
