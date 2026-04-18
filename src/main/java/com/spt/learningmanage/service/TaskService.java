package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.task.TaskBatchRenameRequest;
import com.spt.learningmanage.model.dto.task.TaskBatchRollbackRequest;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskQueryRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.vo.task.TaskBatchRenameVO;
import com.spt.learningmanage.model.vo.task.TaskBatchRollbackVO;
import com.spt.learningmanage.model.vo.task.TaskVo;

public interface TaskService {
    /**
     * 创建任务，返回任务ID。
     */
    Long create(TaskCreateRequest request);

    /**
     * 根据ID查询任务详情，强制过滤 userId。
     */
    TaskVo getById(Long id);

    /**
     * 分页查询任务列表，强制过滤 userId。
     */
    Page<TaskVo> list(TaskQueryRequest request);

    /**
     * 更新任务信息，强制过滤 userId。
     */
    void update(TaskUpdateRequest request);

    /**
     * 删除任务，强制过滤 userId。
     */
    void delete(Long id);

    TaskBatchRenameVO batchRenameTitles(TaskBatchRenameRequest request);

    TaskBatchRollbackVO rollbackBatchRename(TaskBatchRollbackRequest request);
}
