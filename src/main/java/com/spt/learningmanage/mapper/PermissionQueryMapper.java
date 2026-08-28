package com.spt.learningmanage.mapper;

import com.spt.learningmanage.model.permission.ProjectPermissionRow;
import com.spt.learningmanage.model.permission.ActorPermissionRow;
import com.spt.learningmanage.model.permission.TaskPermissionRow;
import com.spt.learningmanage.model.permission.TeamMemberPermissionRow;
import com.spt.learningmanage.model.permission.WeeklyReviewPermissionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 权限判定所需的只读事实查询。
 *
 * <p>Mapper 只返回数据库事实，不执行权限决策，也不读取 {@code UserHolder}。
 * 项目、任务和周复盘查询从第一版即支持批量 ID，单条授权通过单元素集合复用，
 * 避免后续批量授权再维护另一套 SQL。</p>
 */
@Mapper
public interface PermissionQueryMapper {

    ActorPermissionRow selectActorPermissionRow(
            @Param("actorUserId") Long actorUserId
    );

    List<ProjectPermissionRow> selectProjectPermissionRows(
            @Param("actorUserId") Long actorUserId,
            @Param("projectIds") Collection<Long> projectIds
    );

    List<TaskPermissionRow> selectTaskPermissionRows(
            @Param("actorUserId") Long actorUserId,
            @Param("taskIds") Collection<Long> taskIds
    );

    List<WeeklyReviewPermissionRow> selectWeeklyReviewPermissionRows(
            @Param("actorUserId") Long actorUserId,
            @Param("reviewIds") Collection<Long> reviewIds
    );

    TeamMemberPermissionRow selectTeamMemberPermissionRow(
            @Param("actorUserId") Long actorUserId,
            @Param("teamId") Long teamId,
            @Param("targetUserId") Long targetUserId
    );
}
