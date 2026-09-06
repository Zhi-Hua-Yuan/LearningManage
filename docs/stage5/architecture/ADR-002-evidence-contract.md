# ADR-002: Evidence and citation contract

Status: `ACCEPTED`

## Decision

The final context is JSON with server-assigned evidence IDs `S1..S8`. Evidence text is explicitly declared untrusted in the system prompt. The model returns exactly:

```json
{"answer":"... [S1]","insufficientEvidence":false,"citations":["S1"]}
```

The backend requires the declared citation set to equal the markers in the answer. Every ID must exist in the final context; the model never supplies business IDs. One format-repair call is allowed. A second failure returns a safe error and persists no result.

When no authorized candidate survives, Chat is not called. The backend returns the deterministic insufficient-evidence response.

RAG calls use `METADATA_ONLY` AI logging. The call log contains evidence IDs and hashes, never the question, evidence text, or answer body. The answer is retained only in `ai_rag_result` under the configured lifecycle.
