# Stage 7 release runbook

1. Freeze exact backend and frontend SHAs; require clean protected branches.
2. Run `stage7-production-ops.yml` and preserve its diagnostics artifact.
3. Run the frontend test, lint, build and 60-operation contract gates.
4. Run the protected cross-repository candidate against runtime OpenAPI; every
   frontend operation must exist and Stage 0-6 operations must remain present.
5. Resolve every non-MySQL container image to a reviewed digest and replace the
   release candidate's mutable tag references before final sealing.
6. Back up the target MySQL database and restore it to an isolated instance.
7. Run Flyway validate, then V7→V8 on the restored instance; execute the full
   Stage 0-7 smoke suite.
8. Apply V8 with the migration account before deploying the application.
9. Deploy Redis/Prometheus/Tempo/Grafana and the backend with Cleanup disabled.
10. Verify private Actuator exposure, dashboards, alerts and telemetry scans.
11. Enable Knowledge Worker, RAG and Agent in the frozen Stage 6 order.
12. Run a full cleanup Dry Run, review counts, then run one formal cleanup.
13. Run `stage7-real-observability.yml` for the exact backend SHA.
14. Attach the migration manifest, candidate manifest, dashboards, alert rules,
    test evidence and SHA-256 seal to `stage7-v1.0.0`.

Do not create the final tags while any protected runner or cross-repository gate
is pending. V8 is forward-only; a defect is repaired by a new migration, not by
editing the released V8 file.
