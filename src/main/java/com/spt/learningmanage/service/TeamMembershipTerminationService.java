package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest;
import com.spt.learningmanage.model.vo.team.TeamMembershipTerminationVO;

/** 团队成员退出和移除的应用服务。 */
public interface TeamMembershipTerminationService {

    /** 当前登录用户主动退出团队。 */
    TeamMembershipTerminationVO leaveTeam(Long teamId);

    /** 管理员移除指定团队成员。 */
    TeamMembershipTerminationVO removeMember(TeamMemberRemoveRequest request);
}
