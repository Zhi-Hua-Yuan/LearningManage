# Stage 6 risk register

| Risk | Control | Residual status |
|---|---|---|
| Async worker loses request identity | Persist actor/target; explicit ToolExecutionContext; no UserHolder in workers | Low |
| Stale worker overwrites a newer attempt | lease + execution token + CAS terminal update | Low |
| Prompt requests an unregistered/write Tool | registry and per-scene allow-list; no write Tool implementation | Low |
| Project/team changes during analysis | monotonic data version; PARTIAL result; confirmation fails stale | Medium |
| Private member data enters public summary | aggregate-only second prompt; alias rejection; deterministic fallback | Low |
| RAG evidence changes before confirmation | current PermissionService and Stage 5 hash verification | Low |
| Provider Tool Calling fails | fixed read-only workflow and explicit partial reason | Medium |
| Agent executor saturation | bounded executor; durable lease-based retry; per-user admission limit | Low |
| Tool logs leak source bodies | metadata-only summaries and hashes | Low |

