-- Two repairable groups: one business ID and one NULL business ID.
INSERT INTO `ai_draft_confirm_log` (
    `id`, `user_id`, `draft_id`, `operation_id`, `scene`, `business_id`, `create_time`
) VALUES
    (8411, 1101, 'stage2-v3-confirmed', 'stage2-op-equivalent',
     'task-breakdown', 4101, '2026-09-04 09:05:01'),
    (8412, 1101, 'stage2-v3-confirmed-null', 'stage2-op-null-equivalent',
     'weekly-polish', NULL, '2026-09-04 09:06:01');

