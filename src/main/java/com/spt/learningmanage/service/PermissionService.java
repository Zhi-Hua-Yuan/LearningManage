package com.spt.learningmanage.service;

import com.spt.learningmanage.model.permission.ProjectAccessScope;

/**
 * 统一资源权限服务。
 *
 * <p>调用方显式提供 actor 和资源 ID；权限服务只根据服务端查询事实做判定。
 * 具体业务写入、分配日志和复盘 DTO 不属于本接口。</p>
 */
public interface PermissionService {

    ProjectAccessScope requireProjectView(Long actorUserId, Long projectId);

    ProjectAccessScope requireProjectCreateTask(Long actorUserId, Long projectId);

    ProjectAccessScope requireProjectManage(Long actorUserId, Long projectId);

    ProjectAccessScope requireProjectMemberList(Long actorUserId, Long projectId);

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
}
