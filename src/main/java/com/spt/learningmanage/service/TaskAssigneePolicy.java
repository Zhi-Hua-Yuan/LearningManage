package com.spt.learningmanage.service;

import com.spt.learningmanage.model.permission.ProjectAccessScope;

/** 根据可信项目范围解析新任务的初始负责人。 */
public interface TaskAssigneePolicy {

    Long resolveInitialAssignee(ProjectAccessScope scope, Long requestedAssigneeUserId);

    /** 校验负责人变更目标；个人项目允许显式解除负责人。 */
    void validateAssignmentTarget(ProjectAccessScope scope, Long targetAssigneeUserId);
}
