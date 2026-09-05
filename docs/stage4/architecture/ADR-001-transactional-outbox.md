# ADR-001: Transactional Outbox and reconciliation semantics

Status: `ACCEPTED`

## Decision

Business services insert `ai_knowledge_index_event` rows through `KnowledgeIndexEventPublisher` in the same MySQL transaction as the mutation. Spring in-memory events and database triggers are not the delivery mechanism.

Events are wake-up/audit records, not authoritative snapshots. A worker always re-reads current MySQL state and reconciles the full desired document set for the source. Event types explain why reconciliation was requested but never instruct the worker to blindly insert or delete a stale representation.

`worker-enabled=false` stops consumers only. Event capture remains on after V4 so enabling the worker later cannot miss changes.

## Concurrency protocol

- Claim events with `SELECT ... FOR UPDATE SKIP LOCKED` and a short transaction.
- Fence event transitions with `claim_token`.
- Serialize one source through `ai_knowledge_source_lock` with a renewable lease.
- Re-read the source after vector writes. If content or access changed, enqueue a corrective event before completing the current event.
- Lease expiry creates a new token; stale workers cannot update MySQL terminal state.

## Failure policy

Transient network, rate-limit, timeout, and upstream failures enter `RETRY_WAIT`. Configuration, protocol, or vector-dimension failures enter `DEAD`. DEAD replay is explicit and restricted to SYSTEM_ADMIN.
