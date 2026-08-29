package com.spt.learningmanage.service;

import com.spt.learningmanage.model.permission.ProjectAccessScope;

/** 根据可信项目范围解析新任务的初始负责人。 */
public interface TaskAssigneePolicy {

    Long resolveInitialAssignee(ProjectAccessScope scope, Long requestedAssigneeUserId);

    /** 校验负责人变更目标；个人项目允许显式解除负责人。 */
    void validateAssignmentTarget(ProjectAccessScope scope, Long targetAssigneeUserId);

    /**
     * 校验任务重新打开时继续保留的当前负责人。
     *
     * <p>与分配目标校验不同，负责人已经存在但在重新打开时失效属于
     * 当前业务状态冲突，应返回操作失败而不是参数错误。</p>
     */
    void validateReopenAssignee(ProjectAccessScope scope, Long currentAssigneeUserId);
}
