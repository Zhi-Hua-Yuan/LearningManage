# Stage 6 release readiness

Status: `LOCAL CANDIDATE READY / REMOTE AND REAL-PROVIDER GATES BLOCKED`

The committed backend and frontend SHAs are bound in
`stage6-local-candidate-manifest.json`. Local migration, application-path,
security, Promptfoo, frontend and runtime API contract gates pass.

The candidate must not be tagged or released until both conditions are met:

1. GitHub authentication is restored and the Stage 6 workflows pass for the
   exact committed SHA pair.
2. Protected Qwen Tool Calling smoke records model, finish reason, Tool rounds,
   provider request IDs, usage, cost and terminal report-draft behavior.

No local or remote Stage 6 Release is claimed by this document.
