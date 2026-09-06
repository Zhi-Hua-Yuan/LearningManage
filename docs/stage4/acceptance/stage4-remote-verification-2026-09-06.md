# Stage 4 remote verification

Status: `DETERMINISTIC_PASS / PROTECTED_REAL_EMBEDDING_PENDING`

## Merge identity

```text
PR: #136
PR head: 3a1e2cf82a7d2458b33d2c52cf05b21ae036c485
develop merge: 525f681dc7563147ff36964973c56343861a0b47
final hardening PR: #138
final hardening head: 2f50c6c1f95562d8230b648ebe9a3b5821f1d55f
final hardening develop merge: 3434b8856c4c9721f3fad866b3d5d7030ba66436
```

## Candidate gates

| Workflow | Run | Result | Evidence |
|---|---:|---|---|
| Backend CI | 33982828631 | PASS | guard, 764 tests, V4 empty/upgrade, Docker runtime |
| Stage 4 knowledge index | 33982828637 | PASS | MySQL-Outbox-EmbeddingStub-Qdrant create/update/delete |
| Stage 3 AI Evaluation | 33982828668 | PASS | dataset/config contracts and production-path offline evaluation |
| Final Backend CI | 34005358480 | PASS | guard, exact 766 tests, V4 empty/upgrade, Docker runtime |
| Final Stage 4 knowledge index | 34005358486 | PASS | five MySQL-Outbox-EmbeddingStub-Qdrant acceptance scenarios |
| Final Stage 3 AI Evaluation | 34005358481 | PASS | regression after final hardening |

## Post-merge gates

| Workflow | Run | Result |
|---|---:|---|
| Backend CI | 33983320260 | PASS |
| Stage 3 AI Evaluation | 33983320203 | PASS |
| Final Backend CI | 34005709679 | PASS |
| Final Stage 3 AI Evaluation | 34005709738 | PASS |

The final post-merge Backend CI passed:

- migration and secret guards;
- 766/766 Maven tests and exact-count assertion;
- V4 empty-database migration;
- legacy V1 baseline followed by V2, V3, and V4 upgrade;
- tested JAR checksum and Docker runtime/API gate.

The final Stage 4 integration gate passed five end-to-end scenarios:

```text
task create
-> transactional Outbox
-> deterministic Embedding Stub
-> Qdrant upsert
-> payload-only task update without re-embedding
-> task delete
-> document tombstone and Qdrant removal

INITIAL backfill + idempotent runKey
-> zero-difference document/Point reconciliation
-> REBUILD forces fresh embeddings
-> one deterministic Point per one-chunk document

TEAM review -> PRIVATE
-> TEAM Point removed
membership removed
-> PRIVATE and TEAM Points removed

Embedding 429
-> committed task retained + RETRY_WAIT
-> dependency recovery + SUCCESS
missing provider model
-> DEAD + sanitized failure
-> SYSTEM_ADMIN replay + SUCCESS
```

The representative freshness gate drains 100 committed task/Outbox events through the real queue and four concurrent workers against MySQL, the deterministic Embedding HTTP service, and Qdrant. It calculates P95 from MySQL-generated `event.create_time` to MySQL-generated `document.indexed_at` and fails above 60 seconds, including when application and database hosts use different time zones. A hash/UUID uniqueness test is not treated as freshness evidence.

The final hardening also verifies the frozen Qdrant payload names (`userId`, `teamId`,
`projectId`, `sourceType`, `sourceId`, `sourceVersion`, `updatedAt`), RFC 3339 datetime
values, payload indexes, and the absence of Spring's scheduled-annotation processor in the
manual-worker integration context. Both review findings were resolved before merge.

## Pending protected evidence

`Stage 4 real embedding validation` was dispatched for final evidence merge
`f5a590d9c3e8699e72abc0914c35251822208f3b` as workflow run
`34006943574`. Immutable-SHA checkout and Java setup passed, but the protocol test stopped
before any provider request because `ALIYUN_API_KEY` was empty in the protected
`real-ai-validation` environment. The sanitized report was uploaded successfully.

The same missing-secret result occurred in earlier run `34003094202`, so this is an
external environment configuration blocker rather than a transient test failure. Add a
valid repository Environment secret named `ALIYUN_API_KEY`, then rerun the workflow for
the latest `develop` SHA. The workflow fixes the requested model to
`text-embedding-v4`; the client rejects responses that omit the executed model, and the
IT requires exact model equality, non-null usage, ten synthetic inputs, and 1024
dimensions. Until it passes, the stage is implementation-complete but not
final-release sealed.
