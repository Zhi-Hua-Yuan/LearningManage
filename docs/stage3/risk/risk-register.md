# Stage 3 risk register

| Risk | Control | Residual status |
|---|---|---|
| Evaluation accidentally targets a real database | `_eval` suffix, loopback-only host, non-3306 port, CI database allowlist | Low |
| Prompt drift is hidden by copied test prompts | Provider calls the existing public API; manifest hashes built-in Prompt source | Low |
| Holdout leakage produces optimistic results | Protected release-only execution and mandatory rotation after use for tuning | Medium |
| Judge bias or self-grading | Grader model must differ; 20% human review; agreement gate | Medium |
| Missing Usage is recorded as zero | Null-specific assertion; real release requires 100% Usage presence | Low |
| Concurrent cases create false write alarms | Only formal project/milestone/task counts are compared; preview/audit tables are excluded | Low |
| Raw output leaks sensitive data | Synthetic inputs, `--no-share`, ignored raw files, secret scan, 30-day artifact retention | Low |
| Provider or model price changes invalidate cost comparison | Record model and price version; rebuild monetary baseline from Token counts | Medium |
| Stub behavior diverges from real model protocol | Protected real-model regression and holdout runs remain mandatory | Medium |
