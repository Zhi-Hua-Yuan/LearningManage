-- Stage 3: allow a disconnected streaming request to reach a terminal audit state.
-- This is forward-only; published V1-V8 migrations remain immutable.
ALTER TABLE `ai_rag_query_log`
    DROP CHECK `chk_rql_status`;

ALTER TABLE `ai_rag_query_log`
    ADD CONSTRAINT `chk_rql_status` CHECK (
        BINARY `status` IN ('RUNNING', 'SUCCEEDED', 'INSUFFICIENT', 'FAILED', 'CANCELED')
    );
