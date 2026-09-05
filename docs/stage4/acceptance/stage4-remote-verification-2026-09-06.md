# Stage 4 remote verification

Status: `DETERMINISTIC_PASS / PROTECTED_REAL_EMBEDDING_PENDING`

## Merge identity

```text
PR: #136
PR head: 3a1e2cf82a7d2458b33d2c52cf05b21ae036c485
develop merge: 525f681dc7563147ff36964973c56343861a0b47
```

## Candidate gates

| Workflow | Run | Result | Evidence |
|---|---:|---|---|
| Backend CI | 33982828631 | PASS | guard, 764 tests, V4 empty/upgrade, Docker runtime |
| Stage 4 knowledge index | 33982828637 | PASS | MySQL-Outbox-EmbeddingStub-Qdrant create/update/delete |
| Stage 3 AI Evaluation | 33982828668 | PASS | dataset/config contracts and production-path offline evaluation |

## Post-merge gates

| Workflow | Run | Result |
|---|---:|---|
| Backend CI | 33983320260 | PASS |
| Stage 3 AI Evaluation | 33983320203 | PASS |

The post-merge Backend CI passed:

- migration and secret guards;
- 764/764 Maven tests and exact-count assertion;
- V4 empty-database migration;
- legacy V1 baseline followed by V2, V3, and V4 upgrade;
- tested JAR checksum and Docker runtime/API gate.

The Stage 4 integration gate passed:

```text
task create
-> transactional Outbox
-> deterministic Embedding Stub
-> Qdrant upsert
-> payload-only task update without re-embedding
-> task delete
-> document tombstone and Qdrant removal
```

## Pending protected evidence

`Stage 4 real embedding validation` must be manually dispatched for the merge SHA with the protected `real-ai-validation` environment. It must confirm ten synthetic inputs, model `text-embedding-v4`, non-null usage, and 1024 dimensions. Until it passes, the stage is implementation-complete but not final-release sealed.
