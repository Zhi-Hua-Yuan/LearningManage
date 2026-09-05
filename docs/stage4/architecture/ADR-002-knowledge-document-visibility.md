# ADR-002: Knowledge document visibility and content boundary

Status: `ACCEPTED`

## Decision

Knowledge visibility is derived from business facts and uses only `PRIVATE` or `TEAM`.

- Personal-project task: one PRIVATE document owned by the project owner.
- Team-project task: one TEAM document scoped to the project team.
- PRIVATE weekly review: one PRIVATE document only when `focusProjectId` exists and the author still has project access.
- TEAM weekly review: one author PRIVATE document plus one TEAM document. The TEAM document uses only `sharedSummary`.
- Deleted projects/tasks/reviews and invalid project links produce no active document.

The stable key is `{sourceType}:{sourceId}:{visibilityType}:{projectId}`. A visibility or project change creates the new desired key and tombstones/deletes all obsolete keys for that source.

Semantic task text is title plus description. Status, priority, due date, assignee ID, and access scope are payload only. This avoids paying for a new embedding when only operational metadata changes.

No complete source text or vector is stored in `ai_knowledge_document` or operational logs.
