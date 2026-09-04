CREATE UNIQUE INDEX `uk_ai_confirm_user_draft`
    ON `ai_draft_confirm_log` (`user_id`, `draft_id`);
