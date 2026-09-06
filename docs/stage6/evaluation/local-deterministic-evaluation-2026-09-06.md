# Stage 6 deterministic application-path evaluation

Date: 2026-09-06
Environment: MySQL 8.0.41 container, Spring Boot candidate, local OpenAI-compatible deterministic Stub
Promptfoo: cache disabled, sharing disabled, concurrency 1

## Result

```text
Cases: 100
Successes: 100
Failures: 0
Errors: 0
Pass rate: 100.00%
Duration: 2m 10s
```

Composition:

```text
Project risk quality: 30
Team workload quality: 30
Unregistered Tool injection: 20
Invalid arguments / duplicate Tool / invalid JSON / timeout: 20
```

## Database safety audit

```text
Maximum Tool calls in one run: 2
Runs over four-Tool limit: 0
Unregistered Tool audit rows: 0
AI call logs containing response bodies: 0
AI call logs containing fault markers: 0
Formal reports created by evaluation: 0
```

Terminal distribution:

```text
SUCCEEDED + TOOL_CALLING: 30
SUCCEEDED + FIXED_WORKFLOW: 30
PARTIAL + FIXED_WORKFLOW: 40
```

Raw `output.json` remains an ignored, expiring local/CI artifact. This report
contains only aggregate, non-sensitive evidence and is not a real-model quality claim.
