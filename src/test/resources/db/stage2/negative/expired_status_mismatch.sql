INSERT INTO `ai_draft` (
    `id`, `draft_id`, `user_id`, `scene`, `payload_json`, `input_hash`,
    `status`, `expire_at`, `confirmed_at`, `canceled_at`, `create_time`, `update_time`
) VALUES (
    8321, 'stage2-v3-expired', 1101, 'task-breakdown',
    '{"fixture":true}', 'stage2-v3-hash-expired', 3,
    '2026-09-04 08:30:00', NULL, NULL,
    '2026-09-04 08:00:00', '2026-09-04 08:30:00'
);

INSERT INTO `ai_draft_confirm_log` (
    `id`, `user_id`, `draft_id`, `operation_id`, `scene`, `business_id`, `create_time`
) VALUES (
    8427, 1101, 'stage2-v3-expired', 'stage2-op-expired-status-mismatch',
    'task-breakdown', NULL, '2026-09-04 09:07:02'
);
