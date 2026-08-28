package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.dto.task.TaskAssignmentHistoryQueryRequest;
import com.spt.learningmanage.model.vo.task.TaskAssignmentHistoryVO;
import com.spt.learningmanage.model.vo.task.TaskAssignVO;

public interface TaskAssignmentService {
    TaskAssignVO assign(TaskAssignRequest request);

    Page<TaskAssignmentHistoryVO> listAssignmentHistory(
            TaskAssignmentHistoryQueryRequest request
    );
}
