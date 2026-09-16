package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.query.team.MembershipTaskCleanupRow;
import com.spt.learningmanage.model.query.review.WeeklyReviewFocusProjectRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

	Long countWeeklyCompletedTasksByAssignee(
			@Param("assigneeUserId") Long assigneeUserId,
			@Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTimeExclusive") LocalDateTime endDateTimeExclusive
	);

	WeeklyReviewFocusProjectRow selectWeeklyFocusProjectByAssignee(
			@Param("assigneeUserId") Long assigneeUserId,
			@Param("startDateTime") LocalDateTime startDateTime,
			@Param("endDateTimeExclusive") LocalDateTime endDateTimeExclusive
	);

	/**
	 * 锁定某个团队内当前分配给该成员的所有未完成任务。
	 * 这里的 SQL 有意不按逻辑删除或项目归档状态过滤，
	 * 因为被恢复的资源不应继续保留无效负责人。
	 */
	List<MembershipTaskCleanupRow> selectIncompleteAssignedTeamTasksForUpdate(
			@Param("teamId") Long teamId,
			@Param("memberUserId") Long memberUserId
	);

	/**
	 * 对先前已加锁的任务集合清空负责人，并记录操作人/操作时间。
	 * 调用方会在同一事务内将返回影响行数与已加锁行数进行比对。
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
	 * 仅当任务的预期状态与预期负责人仍与资格校验时的快照一致时，才允许重开已完成任务。
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

	/**
	 * 仅当任务仍与预览快照完全一致时，才应用一条已落库的 AI 重规划项。
	 * MySQL 的空值安全比较可将可空截止日期与快照时间戳纳入同一 CAS 条件。
	 */
	@Update("""
			UPDATE task
			SET title = #{newTitle},
			    priority = #{newPriority},
			    due_date = #{newDueDate}
			WHERE id = #{taskId}
			  AND project_id = #{projectId}
			  AND status = #{expectedStatus}
			  AND is_delete = 0
			  AND title <=> #{oldTitle}
			  AND priority <=> #{oldPriority}
			  AND due_date <=> #{oldDueDate}
			  AND update_time <=> #{expectedUpdateTime}
			""")
	int compareAndSetReplan(@Param("taskId") Long taskId,
							 @Param("projectId") Long projectId,
							 @Param("expectedStatus") Integer expectedStatus,
							 @Param("oldTitle") String oldTitle,
							 @Param("oldPriority") Integer oldPriority,
							 @Param("oldDueDate") LocalDate oldDueDate,
							 @Param("expectedUpdateTime") LocalDateTime expectedUpdateTime,
							 @Param("newTitle") String newTitle,
							 @Param("newPriority") Integer newPriority,
							 @Param("newDueDate") LocalDate newDueDate);

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
