package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.team.TeamCreateRequest;
import com.spt.learningmanage.model.dto.team.TeamJoinRequest;
import com.spt.learningmanage.model.dto.team.TeamMemberRoleUpdateRequest;
import com.spt.learningmanage.model.vo.team.TeamCreateVO;
import com.spt.learningmanage.model.vo.team.TeamMemberVO;
import com.spt.learningmanage.model.vo.team.TeamVO;

import java.util.List;

/**
 * 团队服务
 */
public interface TeamService {

    /**
     * 创建团队
     */
    TeamCreateVO createTeam(TeamCreateRequest request);

    /**
     * 通过邀请码加入团队
     */
    void joinTeam(TeamJoinRequest request);

    /**
     * 查询当前用户加入或创建的团队列表
     */
    List<TeamVO> listMyTeams();

    /**
     * 查询团队成员列表
     */
    List<TeamMemberVO> listTeamMembers(Long teamId);

    /**
     * 修改团队成员角色
     */
    void updateMemberRole(TeamMemberRoleUpdateRequest request);

    /**
     * 当前用户主动退出团队。退出时会原子解除其在团队项目中的未完成任务。
     */
    void leaveTeam(Long teamId);

    /**
     * 由团队 OWNER/ADMIN 移除成员。移除时会原子解除目标成员的未完成任务。
     */
    void removeMember(Long teamId, Long targetUserId);
}
