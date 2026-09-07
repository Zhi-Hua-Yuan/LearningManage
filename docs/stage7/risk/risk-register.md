# Stage 7 risk register

| ID | Risk | Mitigation | Exit evidence |
|---|---|---|---|
| S7-R1 | Actuator exposed publicly | private port, no Compose host mapping, exposure allow-list | network negative test |
| S7-R2 | Metric-cardinality explosion | tag allow-list and static contract test | meter inspection |
| S7-R3 | Cleanup deletes unexpected rows | resource hash, approved Dry Run, drift guard, batches | isolated V8 rehearsal |
| S7-R4 | Worker duplicate execution | row lock, lease and execution-token fencing | concurrency test |
| S7-R5 | AI body appears in telemetry | metadata-only instrumentation and secret/body scan | telemetry scan |
| S7-R6 | Redis remains unauthenticated | Redis 7 ACL user, anonymous denial | Docker ACL gate |
| S7-R7 | Telemetry outage affects APIs | non-blocking export and best-effort metrics | Tempo/Prometheus outage drill |
| S7-R8 | Existing MySQL tests unavailable locally | CI isolated database remains release authority | protected CI run |
