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

## External acceptance pending

- The local Docker daemon rejects access, so the MySQL-Qdrant end-to-end test cannot run locally.
- `gh auth status` reports an invalid GitHub token and GitHub API access is unavailable, so PR creation, Linux Runner execution, Tag, Release, image digest binding, and remote evidence sealing cannot be performed in this session.
- The protected real `text-embedding-v4` smoke requires restored GitHub credentials and protected `ALIYUN_API_KEY`; no real credential is placed in local evidence.
- The manual `stage4-real-embedding.yml` workflow and gated `RealEmbeddingValidationIT` are implemented and ready for that protected run.

## Expected remote gates

```text
backend Maven suite: 763 tests
Stage 4 dedicated IT: KnowledgeIndexEndToEndIT
Flyway head: V4
business tables: 27
Flyway history rows: 4
Embedding dimension: 1024
private fields in TEAM vectors: 0
duplicate/stale points after replay/rebuild: 0
```

This document proves local implementation and deterministic verification only. Stage 4 remains remote-acceptance pending until the protected workflows pass.
