# Stage 3 deferred acceptance decision

Status: **IMPLEMENTATION_COMPLETE / ACCEPTANCE_DEFERRED**

Decision date: **2026-09-05**

Reopen milestone: **Stage 7 final AI acceptance**

## Decision

The Stage 3 evaluation implementation is complete, but protected real-model qualification, human semantic review, and final evidence sealing are intentionally deferred to Stage 7. Development may proceed to Stage 4 RAG work without presenting Stage 3 as fully accepted or released.

No Stage 3 GitHub Release or annotated release tag will be published under this decision. Stage 7 may close the evidence in the repository; publishing a GitHub Release requires a separate future decision.

## Reason

The project is prioritizing the higher-value RAG and controlled-Agent capabilities needed for the final product demonstration. Running and reviewing the complete model-quality matrix now would delay those capabilities and would need to be expanded again after RAG and Agent are implemented.

This is a scheduling decision, not a passing quality result and not a reduction of any threshold in `../requirements/stage3-acceptance-contract.json`.

## Frozen handoff baseline

| Item | Frozen value |
|---|---|
| PR | `#134` |
| Backend merge SHA | `5a98b4b9b39fef1303c0d68b653dbf2d3c129a7f` |
| Prompt v2 candidate SHA | `c75bbfc3f3efb5bae01e0f370224371fc3fa0a54` |
| Paired frontend SHA | `9d6f1102fc8df2025478ce9d0eba3d3deccd719d` |
| Dataset version | `1.1.0` |
| Combined dataset digest | `025AFAC654E2EDD10B57EEE3336742AEA9C0F765A06C42A2EBE13D7C0C0F201C` |
| Dataset manifest file SHA-256 | `9559DC1A400B718ADAA98DBDF1C08206CBDA0BB918A27D3A8C180298B2AB7746` |
| Prompt manifest file SHA-256 | `61C4456BBE765AD539FD637DE553AF3EBA243B51BA7C0C60E82B6D7523E6E5DB` |
| Last diagnostic protected run | `33965002036` |

The hashes above identify the handoff point. If code, datasets, Prompt content, model configuration, pricing, assertions, or fixtures change before Stage 7, the Stage 7 operator must generate new manifests and must not reuse these hashes as current evidence.

## Completed before deferral

- Locked Promptfoo toolchain and reproducible configuration validation.
- 170 synthetic quality cases and 40 fault-injection cases.
- Production-API provider covering the five existing AI scenes and six Prompt codes.
- Deterministic structure, business-ID, permission, degradation, Trace, Usage, cost, and no-formal-write assertions.
- Prompt v2 candidate and version/hash manifest support.
- Protected real-model workflow, multi-round aggregation, human-review sampling, and evidence scripts.
- Local deterministic contracts, backend regression, and Docker application-path verification recorded in `../reports/current-baseline.md`.

## Explicitly deferred to Stage 7

- One protected real-model development qualification run.
- Three protected `qwen-plus` regression rounds.
- Three protected `qwen-plus` holdout rounds.
- Aggregate deterministic, semantic, latency, Token, and cost decisions.
- Random 20% human semantic review and the 80% agreement gate.
- Prompt v2 promotion decision and rollback evidence.
- Final cross-SHA evidence index and acceptance manifest.
- Any Stage 3 GitHub Release or release tag; neither is currently required.

## Known diagnostic result

Protected run `33965002036` evaluated the pre-candidate baseline and stopped after its first regression round. It recorded 100% structure parsing, semantic score `0.8694`, deterministic pass rate `73.53%`, business validation rate `75.76%`, Trace/Usage coverage `97.06%`, P95 latency `22,960 ms`, and zero formal business writes.

That run explains the Prompt v2 changes. It is not a passing baseline and does not validate the merged Prompt v2 candidate.

## Controls that are not deferred

The following remain mandatory on every affected PR during Stages 4-6:

- Existing backend, frontend, Flyway, API-contract, secret-scan, and offline Stage 3 CI.
- Unit and integration tests for new business behavior.
- Permission checks at every RAG retrieval and Agent tool boundary.
- RAG indexing idempotency, deletion, retry, and cross-user/cross-team isolation tests.
- Agent tool allowlisting, typed argument validation, timeout, audit, no-direct-write, and prompt-injection tests.
- Versioned Prompt, dataset, fixture, model, and pricing changes.

Real-model quality scoring and human review are deferred; basic correctness, authorization, data isolation, and write safety are not.

## Stage 7 reopen procedure

1. Freeze the final backend/frontend SHAs and inventory all Prompt, RAG, and Agent changes since this handoff.
2. Regenerate and validate dataset and Prompt manifests instead of assuming the frozen hashes are still current.
3. Re-run all offline deterministic and application-path gates.
4. Run the existing five-scene development, regression, and holdout real-model matrix.
5. Run the RAG retrieval, citation, abstention, deletion, and permission matrix.
6. Run the Agent tool-selection, authorization, injection, timeout, partial-failure, audit, and write-safety matrix.
7. Run the complete goal-to-draft-to-history-to-RAG-to-Agent-to-confirmation end-to-end demonstration.
8. Complete the 20% human semantic review and verify at least 80% agreement with the grader.
9. Generate sanitized reports, manifests, and the evidence index. Keep raw model outputs only as expiring restricted artifacts.
10. Record the final acceptance result in the repository. Do not publish a GitHub Release unless it is separately requested.

Stage 3 becomes fully accepted only after the applicable frozen thresholds pass. Until then, project materials must describe it as evaluation infrastructure complete with final AI acceptance deferred.
