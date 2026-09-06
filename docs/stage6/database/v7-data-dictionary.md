# V7 data dictionary

V7 adds monotonic `data_version` to project/team, Agent correlation columns to
`ai_call_log`, and four tables:

- `ai_agent_run`: durable queue, lease, fencing token, cancellation and terminal state.
- `ai_agent_tool_log`: metadata-only Tool audit, unique by run/attempt/sequence.
- `ai_analysis_report`: immutable confirmed report content with logical deletion.
- `ai_analysis_report_source`: citation metadata and content/payload hashes.

Key invariants:

```text
unique(user_id, scene, client_request_id)
unique(source_run_id, report_type)
tool_count <= 4
attempt_count <= 2
exactly one target column for every scene/report type
```

The migration is forward-only. Disabling Agent leaves V7 tables dormant and
does not affect Stage 0-5 behavior.
