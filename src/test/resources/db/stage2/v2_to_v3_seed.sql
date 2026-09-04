-- Deterministic V2 data for Stage 2 WP1 V3 migration verification.
-- Run only after the frozen V2 schema exists.

INSERT INTO `ai_call_log` (
    `id`, `user_id`, `scene`, `model_name`, `prompt_type`,
    `prompt_template_id`, `prompt_version`, `prompt_source`,
    `request_text`, `response_text`, `status`, `error_message`,
    `cost_time_ms`, `retry_count`, `create_time`, `update_time`
) VALUES (
    8201, 1101, 'task-breakdown', 'qwen-plus', 'task-breakdown',
    NULL, 1, 'builtin', 'stage2-v3-request', 'stage2-v3-response',
    1, NULL, 125, 0, '2026-09-04 09:00:00', '2026-09-04 09:00:01'
);

INSERT INTO `ai_draft` (
    `id`, `draft_id`, `user_id`, `scene`, `payload_json`, `input_hash`,
    `status`, `expire_at`, `confirmed_at`, `canceled_at`,
    `create_time`, `update_time`
) VALUES
    (8301, 'stage2-v3-confirmed', 1101, 'task-breakdown', '{"fixture":true}',
     'stage2-v3-hash-1', 1, '2026-09-04 09:30:00', '2026-09-04 09:05:00', NULL,
     '2026-09-04 09:00:00', '2026-09-04 09:05:00'),
    (8302, 'stage2-v3-confirmed-null', 1101, 'weekly-polish', '{"fixture":true}',
     'stage2-v3-hash-2', 1, '2026-09-04 09:30:00', '2026-09-04 09:06:00', NULL,
     '2026-09-04 09:01:00', '2026-09-04 09:06:00'),
    (8303, 'stage2-v3-preview', 1101, 'task-breakdown', '{"fixture":true}',
     'stage2-v3-hash-3', 0, '2026-09-04 09:30:00', NULL, NULL,
     '2026-09-04 09:02:00', '2026-09-04 09:02:00'),
    (8304, 'stage2-v3-canceled', 1101, 'weekly-polish', '{"fixture":true}',
     'stage2-v3-hash-4', 2, '2026-09-04 09:30:00', NULL, '2026-09-04 09:07:00',
     '2026-09-04 09:03:00', '2026-09-04 09:07:00');

INSERT INTO `ai_draft_confirm_log` (
    `id`, `user_id`, `draft_id`, `operation_id`, `scene`, `business_id`, `create_time`
) VALUES
    (8401, 1101, 'stage2-v3-confirmed', 'stage2-op-original',
     'task-breakdown', 4101, '2026-09-04 09:05:00'),
    (8402, 1101, 'stage2-v3-confirmed-null', 'stage2-op-null-original',
     'weekly-polish', NULL, '2026-09-04 09:06:00');

