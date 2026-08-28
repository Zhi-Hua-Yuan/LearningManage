package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.vo.task.TaskAssignVO;

public interface TaskAssignmentService {
    TaskAssignVO assign(TaskAssignRequest request);
}
