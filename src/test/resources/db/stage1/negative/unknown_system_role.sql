-- Negative V2 preflight fixture: V2-P-010 must fail.
-- Apply after v1_to_v2_seed.sql in an isolated V1 database.

INSERT INTO `user`
    (`id`, `account`, `username`, `password`, `user_role`, `create_time`, `update_time`, `is_delete`)
VALUES
    (1191, 'stage1_v2_unknown_role', 'Stage1 Unknown Role', 'not-a-real-password-hash', 'AUDITOR', '2026-08-06 08:00:00', '2026-08-06 08:00:00', 0);
