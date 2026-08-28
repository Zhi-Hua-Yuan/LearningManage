-- D2-A: additive V2 fixture for TaskAssignmentLogMapper integration tests.
-- The shared permission fixture supplies users and tasks; this file supplies
-- only deterministic assignment history rows.
INSERT INTO `task_assignment_log` (
    `id`, `task_id`, `from_assignee_user_id`, `to_assignee_user_id`,
    `assigned_by_user_id`, `action`, `reason`, `create_time`
) VALUES
    (862001, 62001, NULL, 12001, 12001, 'INITIAL_ASSIGN', 'initial',
        '2026-01-01 09:00:00'),
    (862002, 62001, 12001, 12002, 12001, 'REASSIGN', 'handoff',
        '2026-01-01 10:00:00'),
    (862003, 62001, 12002, NULL, 12002, 'UNASSIGN', NULL,
        '2026-01-01 10:00:00'),
    (862004, 62001, NULL, 12005, 12002, 'ASSIGN', 'deleted target',
        '2026-01-01 11:00:00'),
    (862005, 62001, 12005, 12999, 12998, 'REASSIGN', 'unresolvable users',
        '2026-01-01 12:00:00'),
    (862006, 62002, NULL, 12003, 12001, 'INITIAL_ASSIGN', 'other task',
        '2026-01-01 13:00:00');
