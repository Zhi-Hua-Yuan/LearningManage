# Evidence policy

A sealable result binds all of the following: backend SHA, frontend SHA, dataset version and hash, six Prompt code/version/source/hash values, requested and actual model, grader model, price version, round count, Token totals, P50/P95 latency, failed case IDs, sanitized summary hash, and raw artifact identity.

Raw Promptfoo JSON is retained for 30 days as a restricted GitHub Artifact. The repository and Release contain only sanitized summaries, hashes, failure case IDs, manifests, and the human-review agreement result. API keys, JWTs, full provider request IDs, full free-text questions, and complete model responses are forbidden in release evidence.

`scripts/ci/create-stage3-evidence-index.sh` hashes the durable Stage 3 sources while excluding raw outputs, generated Promptfoo tests, caches, and dependencies. `scripts/ci/create-stage3-release-manifest.sh` binds that index to the dataset manifest, Prompt manifest, six-round candidate summary, human-review result, and exact backend/frontend SHAs.

Current status is `ACCEPTANCE_DEFERRED` under `../release/deferred-acceptance.md`. No seal, release tag, or GitHub Release exists for Prompt v2. Stage 7 must regenerate evidence for the then-current code and manifests; the frozen Stage 3 handoff hashes are traceability anchors, not reusable passing evidence. Repository evidence sealing does not require publishing a GitHub Release.
