-- Stage 0.2 orphan-data repair rehearsal.
-- Scope is intentionally pinned to the isolated restore database.
-- No physical DELETE is used. The script stops on database, candidate-count,
-- or affected-row mismatches when executed by mysql without --force.

USE `learning_manage_baseline_restore_20260817`;

SET @expected_database = 'learning_manage_baseline_restore_20260817';
SET @guard_sql = IF(
    DATABASE() = @expected_database,
    'SELECT ''database guard passed'' AS guard_result',
    'SELECT * FROM __stage0_abort_wrong_database__'
);
PREPARE guard_stmt FROM @guard_sql;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

-- Candidate fingerprints are SHA-256 prefixes of the numeric primary keys.
-- Timestamp and old-value predicates prevent repairing records that changed
-- after the 2026-08-17 audit.
SELECT COUNT(*) INTO @candidate_project_count
FROM `project` p
LEFT JOIN `user` u ON u.id = p.user_id AND u.is_delete = 0
WHERE LEFT(SHA2(CAST(p.id AS CHAR), 256), 12) = '10da6d2c6938'
  AND p.is_delete = 0
  AND p.deleted_at IS NULL
  AND p.create_time = '2026-04-11 00:19:00'
  AND p.update_time = '2026-04-11 00:19:00'
  AND u.id IS NULL;

SELECT COUNT(*) INTO @candidate_task_count
FROM `task` t
LEFT JOIN `project` p ON p.id = t.project_id AND p.is_delete = 0
WHERE LEFT(SHA2(CAST(t.id AS CHAR), 256), 12) IN (
          '80d94ad62151',
          '4693a21c3076'
      )
  AND t.is_delete = 0
  AND t.delete_source = 0
  AND p.id IS NULL;

SELECT @candidate_project_count AS candidate_project_count,
       @candidate_task_count AS candidate_task_count;

SET @guard_sql = IF(
    @candidate_project_count = 1 AND @candidate_task_count = 2,
    'SELECT ''candidate guard passed'' AS guard_result',
    'SELECT * FROM __stage0_abort_candidate_mismatch__'
);
PREPARE guard_stmt FROM @guard_sql;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

SET @repair_time = CURRENT_TIMESTAMP;
START TRANSACTION;

UPDATE `project` p
LEFT JOIN `user` u ON u.id = p.user_id AND u.is_delete = 0
SET p.is_delete = 1,
    p.deleted_at = @repair_time,
    p.update_time = @repair_time
WHERE LEFT(SHA2(CAST(p.id AS CHAR), 256), 12) = '10da6d2c6938'
  AND p.is_delete = 0
  AND p.deleted_at IS NULL
  AND p.create_time = '2026-04-11 00:19:00'
  AND p.update_time = '2026-04-11 00:19:00'
  AND u.id IS NULL;
SET @project_affected = ROW_COUNT();

UPDATE `task` t
LEFT JOIN `project` p ON p.id = t.project_id AND p.is_delete = 0
SET t.is_delete = 1,
    t.delete_source = 1,
    t.update_time = @repair_time
WHERE LEFT(SHA2(CAST(t.id AS CHAR), 256), 12) IN (
          '80d94ad62151',
          '4693a21c3076'
      )
  AND t.is_delete = 0
  AND t.delete_source = 0
  AND p.id IS NULL;
SET @task_affected = ROW_COUNT();

SELECT @repair_time AS repair_time,
       @project_affected AS project_affected,
       @task_affected AS task_affected;

SET @guard_sql = IF(
    @project_affected = 1 AND @task_affected = 2,
    'SELECT ''affected-row guard passed'' AS guard_result',
    'SELECT * FROM __stage0_abort_affected_row_mismatch__'
);
PREPARE guard_stmt FROM @guard_sql;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

COMMIT;

SELECT 'rehearsal committed' AS result,
       @repair_time AS repair_time,
       @project_affected AS project_affected,
       @task_affected AS task_affected;
