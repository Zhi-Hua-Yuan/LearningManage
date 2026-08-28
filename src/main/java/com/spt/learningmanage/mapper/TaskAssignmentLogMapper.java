package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.query.task.TaskAssignmentHistoryRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskAssignmentLogMapper extends BaseMapper<TaskAssignmentLog> {

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
