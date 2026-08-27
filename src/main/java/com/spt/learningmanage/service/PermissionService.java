package com.spt.learningmanage.service;

import com.spt.learningmanage.model.access.ProjectAccessScope;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface PermissionService {

    ProjectAccessScope requireProjectView(Long actorId, Long projectId);

    ProjectAccessScope requireProjectManage(Long actorId, Long projectId);

    void requireTaskView(Long actorId, Long taskId);

    void requireTaskEditContent(Long actorId, Long taskId);

    void requireTaskChangeStatus(Long actorId, Long taskId);

    void requireTaskReorganize(Long actorId, Long taskId);

    void requireTaskAssign(Long actorId, Long taskId);

    void requireTaskDelete(Long actorId, Long taskId);

    void requireWeeklyReviewFullView(Long actorId, Long reviewId);

    void requireWeeklyReviewSharedView(Long actorId, Long reviewId);

    Map<Long, ProjectAccessScope> resolveProjectScopes(Long actorId, Collection<Long> projectIds);

    Set<Long> filterReadableTaskIds(Long actorId, Collection<Long> taskIds);
}
