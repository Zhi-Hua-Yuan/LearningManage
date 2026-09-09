-- WP5-E: cleanup is limited to the dedicated project and ID range.
DELETE FROM ai_knowledge_index_event
WHERE source_type = 'TASK' AND source_id BETWEEN 68001 AND 68005;
DELETE l
FROM task_assignment_log l
JOIN task t ON t.id = l.task_id
WHERE t.project_id IN (48001, 48002);
DELETE i
FROM task_status_idempotency i
JOIN task t ON t.id = i.task_id
WHERE t.project_id IN (48001, 48002);
DELETE FROM task WHERE project_id IN (48001, 48002);
DELETE FROM project WHERE id IN (48001, 48002);
DELETE FROM team_member WHERE team_id = 28001;
DELETE FROM team WHERE id = 28001;
DELETE FROM user WHERE id BETWEEN 18001 AND 18004;
