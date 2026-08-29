-- WP5-E: cleanup is limited to the dedicated project and ID range.
DELETE l
FROM task_assignment_log l
JOIN task t ON t.id = l.task_id
WHERE t.project_id = 48001;
DELETE i
FROM task_status_idempotency i
JOIN task t ON t.id = i.task_id
WHERE t.project_id = 48001;
DELETE FROM task WHERE project_id = 48001;
DELETE FROM project WHERE id = 48001;
DELETE FROM team_member WHERE team_id = 28001;
DELETE FROM team WHERE id = 28001;
DELETE FROM user WHERE id BETWEEN 18001 AND 18004;
