# Stage 4 local implementation verification

Date: 2026-09-06

## Completed locally

- Design, privacy, migration, Outbox, hashing/chunking, worker, backfill, admin API, CI, and rollback contracts implemented.
- V4 immutable migration static/manifest tests pass.
- Embedding and Qdrant REST protocol tests pass.
- Transactional write-path architecture and affected business regression tests pass.
- Normalization, deterministic UUIDv5, document privacy, payload-only update, stale-chunk deletion, forced rebuild, queue fencing, worker failure, backfill, admin authorization, and independent circuit tests pass.
- Docker Compose configuration parses successfully.
- GitHub workflow and Compose YAML parse successfully through the repository's pinned Node YAML parser.
- All 703 tests not requiring MySQL credentials or protected real-provider credentials pass with zero failures/errors.

## Remote deterministic acceptance

- PR #136 was merged as `525f681dc7563147ff36964973c56343861a0b47`.
- Candidate Backend CI `33982828631`, Stage 4 integration `33982828637`, and Stage 3 evaluation `33982828668` passed.
- Post-merge Backend CI `33983320260` and Stage 3 evaluation `33983320203` passed.
- The Linux runner verified 764/764 tests, V4 empty install, V1-to-V4 upgrade, tested-JAR Docker runtime, and the MySQL-Outbox-EmbeddingStub-Qdrant lifecycle.

## Protected acceptance pending

- The protected real `text-embedding-v4` smoke requires manual workflow dispatch and the `real-ai-validation` environment's `ALIYUN_API_KEY`; no real credential is available to this session.
- `stage4-real-embedding.yml` and `RealEmbeddingValidationIT` are ready and use ten synthetic texts only.
- Release/tag and final PASS sealing remain pending that protected run.

## Expected remote gates

```text
backend Maven suite: 764 tests (authoritative count from the first real MySQL runner; includes parameterized database cases)
Stage 4 dedicated IT: KnowledgeIndexEndToEndIT
Flyway head: V4
business tables: 27
Flyway history rows: 4
Embedding dimension: 1024
private fields in TEAM vectors: 0
duplicate/stale points after replay/rebuild: 0
```

This document proves implementation and deterministic remote verification. It intentionally does not claim protected real-provider acceptance.
