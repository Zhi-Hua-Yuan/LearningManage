package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

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
