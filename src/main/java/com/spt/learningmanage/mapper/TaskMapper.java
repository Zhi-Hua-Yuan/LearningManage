package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.query.team.MembershipTaskCleanupRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

	/**
	 * Lock all incomplete tasks currently assigned to a member in one team.
	 * The SQL intentionally does not filter logical deletion or project archive
	 * state because recovered resources must not retain an invalid assignee.
	 */
	List<MembershipTaskCleanupRow> selectIncompleteAssignedTeamTasksForUpdate(
			@Param("teamId") Long teamId,
			@Param("memberUserId") Long memberUserId
	);

	/**
	 * Clear the assignee for the previously locked task set and stamp the
	 * operation actor/time. The caller compares the returned count with the
	 * locked row count inside its transaction.
	 */
	int bulkUnassignIncompleteTeamTasks(
			@Param("teamId") Long teamId,
			@Param("memberUserId") Long memberUserId,
			@Param("taskIds") Collection<Long> taskIds,
			@Param("assignedByUserId") Long assignedByUserId,
			@Param("assignedAt") LocalDateTime assignedAt
	);

	@Update("""
			UPDATE task
			SET assignee_user_id = #{newAssigneeUserId},
			    assigned_by_user_id = #{assignedByUserId},
			    assigned_at = #{assignedAt}
			WHERE id = #{taskId}
			  AND is_delete = 0
			  AND assignee_user_id <=> #{expectedAssigneeUserId}
			""")
	int compareAndSetAssignee(@Param("taskId") Long taskId,
								  @Param("expectedAssigneeUserId") Long expectedAssigneeUserId,
								  @Param("newAssigneeUserId") Long newAssigneeUserId,
								  @Param("assignedByUserId") Long assignedByUserId,
								  @Param("assignedAt") java.time.LocalDateTime assignedAt);

	/**
	 * Reopen a completed task only when both its expected status and expected
	 * assignee still match the snapshot that was qualification-checked.
	 */
	@Update("""
			UPDATE task
			SET status = #{targetStatus},
			    completed_at = #{completedAt}
			WHERE id = #{taskId}
			  AND is_delete = 0
			  AND status = #{expectedStatus}
			  AND assignee_user_id <=> #{expectedAssigneeUserId}
			""")
	int compareAndSetStatusForReopen(@Param("taskId") Long taskId,
									 @Param("expectedStatus") Integer expectedStatus,
									 @Param("expectedAssigneeUserId") Long expectedAssigneeUserId,
									 @Param("targetStatus") Integer targetStatus,
									 @Param("completedAt") LocalDateTime completedAt);

	@Update("""
			UPDATE task
			SET is_delete = 0,
			    deleted_at = NULL,
			    delete_source = 0
			WHERE project_id = #{projectId}
			  AND is_delete = 1
			  AND delete_source = 2
			""")
	int recoverByProjectId(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
