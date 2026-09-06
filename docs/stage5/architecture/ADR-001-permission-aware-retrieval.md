# ADR-001: Permission-aware retrieval and current-state hydration

Status: `ACCEPTED`

## Decision

The application performs three authorization checks:

1. `PermissionService.requireProjectView` before any provider call.
2. Qdrant payload filtering followed by batch task/review authorization and MySQL hydration.
3. Project, citation, and content/payload hash verification immediately before persistence and on every historical-result read.

Personal projects query only `PRIVATE + ownerUserId`. Team projects query `TEAM + teamId` or the requester's own `PRIVATE` documents, always within the requested project.

Qdrant payloads are untrusted. The application uses only `sourceType`, `sourceId`, `documentKey`, `chunkIndex`, and `sourceVersion` to locate a candidate; it then regenerates the desired document and chunk through the Stage 4 factory/normalizer/chunker. Missing, unauthorized, malformed, or stale candidates are discarded. Stale candidates enqueue a corrective Outbox event.

## Consequences

- Vector-store compromise cannot directly inject arbitrary prompt text.
- Permission revocation is effective even before asynchronous vector deletion.
- Retrieval performs more MySQL work, bounded by initial TopK and batch permission queries.
