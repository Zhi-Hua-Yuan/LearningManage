package com.spt.learningmanage.service;

import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.permission.ProjectAccessScope;

/** 普通创建与 AI 创建共用的任务写入入口。 */
public interface TaskCreationService {

    Long createTask(Task task, ProjectAccessScope scope, Long requestedAssigneeUserId);
}
