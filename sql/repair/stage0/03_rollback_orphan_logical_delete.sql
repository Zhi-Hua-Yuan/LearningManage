-- Roll back the Stage 0.2 restore-database rehearsal.
-- The shared repair timestamp is used as a batch marker. If any repaired row
-- changed after the rehearsal, candidate guards fail and nothing is committed.

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

SELECT MAX(deleted_at) INTO @repair_marker
FROM `project`
WHERE LEFT(SHA2(CAST(id AS CHAR), 256), 12) = '10da6d2c6938'
  AND is_delete = 1
  AND deleted_at IS NOT NULL
  AND update_time = deleted_at;

SELECT COUNT(*) INTO @rollback_project_count
FROM `project`
WHERE LEFT(SHA2(CAST(id AS CHAR), 256), 12) = '10da6d2c6938'
  AND is_delete = 1
  AND deleted_at = @repair_marker
  AND update_time = @repair_marker;

SELECT COUNT(*) INTO @rollback_task_count
FROM `task`
WHERE LEFT(SHA2(CAST(id AS CHAR), 256), 12) IN (
          '80d94ad62151',
          '4693a21c3076'
      )
  AND is_delete = 1
  AND delete_source = 1
  AND update_time = @repair_marker;

SELECT @repair_marker AS repair_marker,
       @rollback_project_count AS rollback_project_count,
       @rollback_task_count AS rollback_task_count;

SET @guard_sql = IF(
    @repair_marker IS NOT NULL
        AND @rollback_project_count = 1
        AND @rollback_task_count = 2,
    'SELECT ''rollback candidate guard passed'' AS guard_result',
    'SELECT * FROM __stage0_abort_rollback_candidate_mismatch__'
);
PREPARE guard_stmt FROM @guard_sql;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

START TRANSACTION;

UPDATE `project`
SET is_delete = 0,
    deleted_at = NULL,
    update_time = '2026-04-11 00:19:00'
WHERE LEFT(SHA2(CAST(id AS CHAR), 256), 12) = '10da6d2c6938'
  AND is_delete = 1
  AND deleted_at = @repair_marker
  AND update_time = @repair_marker;
SET @project_affected = ROW_COUNT();

UPDATE `task`
SET is_delete = 0,
    delete_source = 0,
    update_time = CASE LEFT(SHA2(CAST(id AS CHAR), 256), 12)
        WHEN '80d94ad62151' THEN '2026-03-24 19:59:28'
        WHEN '4693a21c3076' THEN '2026-03-25 11:55:51'
        ELSE update_time
    END
WHERE LEFT(SHA2(CAST(id AS CHAR), 256), 12) IN (
          '80d94ad62151',
          '4693a21c3076'
      )
  AND is_delete = 1
  AND delete_source = 1
  AND update_time = @repair_marker;
SET @task_affected = ROW_COUNT();

SET @guard_sql = IF(
    @project_affected = 1 AND @task_affected = 2,
    'SELECT ''rollback affected-row guard passed'' AS guard_result',
    'SELECT * FROM __stage0_abort_rollback_affected_row_mismatch__'
);
PREPARE guard_stmt FROM @guard_sql;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

COMMIT;

SELECT 'rehearsal rolled back' AS result,
       @project_affected AS project_affected,
       @task_affected AS task_affected;
