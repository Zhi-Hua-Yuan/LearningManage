# ADR-004: Canonicalization, chunking, and hashing

Status: `ACCEPTED`

## Decision

`norm-v1` normalizes Unicode to NFC, line endings to LF, trims boundaries, collapses repeated horizontal whitespace, omits empty fields, and renders fields in a fixed order.

`chunk-v1` uses a 1,200-character maximum and 150-character overlap. It prefers paragraph, newline, and sentence boundaries. A task title is repeated in every task chunk.

```text
contentHash = SHA-256(normalizerVersion + chunkingVersion + canonicalText)
payloadHash = SHA-256(canonical JSON payload)
```

- Content hash changed: recompute embeddings and upsert points.
- Payload hash only changed: overwrite payload without an embedding call.
- Neither changed: mark reconciliation successful without an external write.
- Empty semantic text: `SKIPPED/EMPTY_CONTENT` and remove old points.

Hash comparison is repeated after the external write. A mismatch schedules correction rather than allowing the event to claim convergence.
