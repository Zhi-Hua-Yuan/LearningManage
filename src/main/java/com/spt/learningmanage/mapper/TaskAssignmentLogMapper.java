package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.query.task.TaskAssignmentHistoryRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Comparator;
import java.util.List;

@Mapper
public interface TaskAssignmentLogMapper extends BaseMapper<TaskAssignmentLog> {

    /**
     * Batch insert membership-termination logs in deterministic task order.
     * The caller must provide unique generated ids and non-null task ids.
     */
    default int batchInsertMembershipTerminationLogs(List<TaskAssignmentLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0;
        }
        List<TaskAssignmentLog> orderedLogs = logs.stream()
                .sorted(Comparator.comparing(TaskAssignmentLog::getTaskId))
                .toList();
        return doBatchInsertMembershipTerminationLogs(orderedLogs);
    }

    @Insert({
            "<script>",
            "INSERT INTO task_assignment_log (",
            "id, task_id, from_assignee_user_id, to_assignee_user_id,",
            "assigned_by_user_id, action, reason, create_time)",
            "VALUES",
            "<foreach collection='logs' item='log' separator=','>",
            "(#{log.id}, #{log.taskId}, #{log.fromAssigneeUserId},",
            "#{log.toAssigneeUserId}, #{log.assignedByUserId},",
            "#{log.action}, #{log.reason}, #{log.createTime})",
            "</foreach>",
            "</script>"
    })
    int doBatchInsertMembershipTerminationLogs(
            @Param("logs") List<TaskAssignmentLog> logs
    );

    /**
     * 分页查询指定任务的负责人变更历史。
     *
     * <p>调用方必须在执行本方法前完成
     * {@code TASK_ASSIGNMENT_HISTORY_VIEW} 授权。本查询只返回扁平历史行，
     * 不返回 User/Task 实体，也不负责分页参数或权限校验。</p>
     */
    IPage<TaskAssignmentHistoryRow> selectAssignmentHistoryPage(
            Page<TaskAssignmentHistoryRow> page,
            @Param("taskId") Long taskId
    );
}
