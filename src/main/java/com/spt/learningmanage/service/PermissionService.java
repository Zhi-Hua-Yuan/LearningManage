package com.spt.learningmanage.service;

import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.permission.TaskCapabilities;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 统一资源权限服务。
 *
 * <p>调用方显式提供 actor 和资源 ID；权限服务只根据服务端查询事实做判定。
 * 具体业务写入、分配日志和复盘 DTO 不属于本接口。</p>
 */
public interface PermissionService {

    void requireActiveActor(Long actorUserId);

    ProjectAccessScope requireProjectView(Long actorUserId, Long projectId);

    ProjectAccessScope requireProjectCreateTask(Long actorUserId, Long projectId);

    ProjectAccessScope requireProjectManage(Long actorUserId, Long projectId);

    ProjectAccessScope requireProjectMemberList(Long actorUserId, Long projectId);

    ProjectAccessScope requireProjectRecover(Long actorUserId, Long projectId);

    void requireTeamView(Long actorUserId, Long teamId);

    void requireTeamManageProject(Long actorUserId, Long teamId);

    void requireTeamMemberList(Long actorUserId, Long teamId);

    void requireTaskView(Long actorUserId, Long taskId);

    void requireTaskEditContent(Long actorUserId, Long taskId);

    void requireTaskChangeStatus(Long actorUserId, Long taskId);

    void requireTaskReorganize(Long actorUserId, Long taskId);

    void requireTaskAssign(Long actorUserId, Long taskId);

    void requireTaskDelete(Long actorUserId, Long taskId);

    void requireTaskAssignmentHistoryView(Long actorUserId, Long taskId);

    void requireWeeklyReviewFullView(Long actorUserId, Long reviewId);

    void requireWeeklyReviewUpdate(Long actorUserId, Long reviewId);

    void requireWeeklyReviewDelete(Long actorUserId, Long reviewId);

    void requireWeeklyReviewSharedView(Long actorUserId, Long reviewId);

    void requireTeamMemberRoleUpdate(Long actorUserId, Long teamId, Long targetUserId);

    void requireTeamMemberRemove(Long actorUserId, Long teamId, Long targetUserId);

    void requireTeamLeave(Long actorUserId, Long teamId);

    /**
     * Resolve the projects visible to the actor in one batch. Missing or
     * unauthorized projects are omitted from the returned map.
     */
    Map<Long, ProjectAccessScope> resolveProjectScopes(
            Long actorUserId,
            Collection<Long> projectIds
    );

    /**
     * Filter a server-side candidate task set to tasks readable by the actor.
     * This method intentionally has filtering semantics; callers handling
     * client- or model-supplied IDs must use {@link #requireAllTasksReadable}.
     */
    Set<Long> filterReadableTaskIds(Long actorUserId, Collection<Long> taskIds);

    /**
     * Require every supplied task ID to be readable and return the normalized
     * IDs. A missing, deleted, malformed or unauthorized ID rejects the whole
     * request.
     */
    Set<Long> requireAllTasksReadable(Long actorUserId, Collection<Long> taskIds);

    void requireAllTasksEditableContent(Long actorUserId, Collection<Long> taskIds);

    void requireAllTasksReorganizable(Long actorUserId, Collection<Long> taskIds);

    /**
     * Resolve task write capabilities in one batch. Only readable tasks are
     * present in the result map.
     */
    Map<Long, TaskCapabilities> resolveTaskCapabilities(
            Long actorUserId,
            Collection<Long> taskIds
    );
}
