package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.task.TaskBatchRenameRequest;
import com.spt.learningmanage.model.dto.task.TaskBatchRollbackRequest;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskQueryRequest;
import com.spt.learningmanage.model.dto.task.TaskStatusChangeRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.vo.task.TaskBatchRenameVO;
import com.spt.learningmanage.model.vo.task.TaskBatchRollbackVO;
import com.spt.learningmanage.model.vo.task.TaskAssignmentLogVO;
import com.spt.learningmanage.model.vo.task.TaskAssignmentResultVO;
import com.spt.learningmanage.model.vo.task.TaskStatusChangeVO;
import com.spt.learningmanage.model.vo.task.TaskVo;

import java.util.List;

public interface TaskService {
    Long create(TaskCreateRequest request);

    TaskVo getById(Long id);

    Page<TaskVo> list(TaskQueryRequest request);

    void update(TaskUpdateRequest request);

    TaskStatusChangeVO changeStatus(TaskStatusChangeRequest request);

    void delete(Long id);

    TaskAssignmentResultVO assign(TaskAssignRequest request);

    List<TaskAssignmentLogVO> assignmentHistory(Long taskId);

    TaskBatchRenameVO batchRenameTitles(TaskBatchRenameRequest request);

    TaskBatchRollbackVO rollbackBatchRename(TaskBatchRollbackRequest request);
}
