package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.team.TeamCreateRequest;
import com.spt.learningmanage.model.dto.team.TeamJoinRequest;
import com.spt.learningmanage.model.dto.team.TeamMemberRoleUpdateRequest;
import com.spt.learningmanage.model.dto.team.TeamOwnerTransferRequest;
import com.spt.learningmanage.model.dto.team.TeamUpdateRequest;
import com.spt.learningmanage.model.vo.team.TeamCreateVO;
import com.spt.learningmanage.model.vo.team.TeamDissolutionCheckVO;
import com.spt.learningmanage.model.vo.team.TeamDissolutionVO;
import com.spt.learningmanage.model.vo.team.TeamInviteVO;
import com.spt.learningmanage.model.vo.team.TeamMemberVO;
import com.spt.learningmanage.model.vo.team.TeamOwnershipTransferVO;
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

    TeamVO updateTeam(TeamUpdateRequest request);

    TeamInviteVO getInvite(Long teamId);

    TeamInviteVO regenerateInvite(Long teamId);

    TeamOwnershipTransferVO transferOwnership(TeamOwnerTransferRequest request);

    TeamDissolutionCheckVO checkDissolution(Long teamId);

    TeamDissolutionVO dissolveTeam(Long teamId);
}
