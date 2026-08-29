-- D2-E cleanup for the shared permission fixture.
DELETE FROM `task_assignment_log`
WHERE `task_id` IN (62001, 62002, 62003, 62004, 62005);

DELETE FROM `weekly_review_task`
WHERE `weekly_review_id` IN (72001, 72002, 72003, 72004);

DELETE FROM `weekly_review`
WHERE `id` IN (72001, 72002, 72003, 72004);

DELETE FROM `task`
WHERE `id` IN (62001, 62002, 62003, 62004, 62005);

DELETE FROM `project`
WHERE `id` IN (42001, 42002, 42003, 42004);

DELETE FROM `team_member`
WHERE `id` IN (32001, 32002, 32003, 32004, 32005, 32006, 32007);

DELETE FROM `team`
WHERE `id` IN (22001, 22002, 22003);

DELETE FROM `user`
WHERE `id` IN (12001, 12002, 12003, 12004, 12005, 12006, 12007);
