# Stage 3 Prompt v2 candidate

Status: **IMPLEMENTED / REAL-MODEL QUALIFICATION DEFERRED TO STAGE 7**

## Evidence that triggered this candidate

Protected workflow run `33965002036` completed the first regression round and preserved its raw and sanitized evidence before the gate stopped the workflow.

The completed round contained 34 cases. Structure parsing was 100%, semantic quality averaged 0.8694, the lowest per-scene semantic average was 0.735, and formal business writes remained zero. It did not qualify because deterministic pass rate was 73.53%, business validation was 75.76%, Trace/Usage/provider-request correlation was 97.06%, and P95 latency was 22,960 ms.

The dominant product failures were concentrated in task breakdown: requested planning windows were exceeded, the detailed response returned an invalid task count, one response failed business parsing, and one short-cycle plan was judged insufficiently feasible. One daily rename changed an already clear title. A today-order semantic grade was also lost to a transient grader network failure.

This failed run is diagnostic evidence only. It is not a Stage 3 release baseline and none of its failing metrics are presented as a passing result.

## Candidate changes

- `task-breakdown.default` v2 fixes the response shape at three milestones with three tasks per milestone and requires every due date to remain inside the supplied inclusive planning window.
- `task-breakdown.detailed` v2 fixes the response shape at three milestones with four tasks per milestone, applies the same date boundary, and requires 15–90 minute microtasks for short cycles.
- `daily-review-rename.default` v2 returns no edit for titles that are already clear and actionable and forbids expanding their scope.
- The task-breakdown runtime prompt now supplies an explicit latest due date when the duration can be parsed.
- Dataset v1.1.0 represents both task-breakdown Prompt codes in every quality split; task-breakdown labels advance to version 2.
- The semantic grader retries bounded transient network and provider failures without changing the fixed grader model.
- The protected workflow adds a development qualification round, preserves every round's evidence before exiting, and applies semantic thresholds to the complete three-round regression and holdout aggregates.

The candidate Prompt rows are installed only into a protected `_eval` database. They do not modify a production Prompt until all release gates pass and a separate promotion operation is approved.

## Acceptance decision

PR `#134` merged the candidate implementation at backend SHA `5a98b4b9b39fef1303c0d68b653dbf2d3c129a7f`. The project may use that engineering baseline to continue Stage 4 development, but the merge is not a real-model quality approval.

No Stage 3 threshold is weakened. Qualification is deferred under `../release/deferred-acceptance.md`. In Stage 7 this candidate, or its then-current replacement, must pass the development qualification, three regression rounds, three holdout rounds, deterministic and security gates on every round, aggregate semantic gates, performance and cost gates, and the 20% human-review agreement check before any verified-quality or promotion claim.

No Stage 3 GitHub Release or release tag will be published now.
