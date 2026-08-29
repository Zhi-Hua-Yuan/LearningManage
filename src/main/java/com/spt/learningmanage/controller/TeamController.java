package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.dto.team.TeamCreateRequest;
import com.spt.learningmanage.model.dto.team.TeamJoinRequest;
import com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest;
import com.spt.learningmanage.model.dto.team.TeamMemberRoleUpdateRequest;
import com.spt.learningmanage.model.vo.team.TeamCreateVO;
import com.spt.learningmanage.model.vo.team.TeamMemberVO;
import com.spt.learningmanage.model.vo.team.TeamMembershipTerminationVO;
import com.spt.learningmanage.model.vo.team.TeamVO;
import com.spt.learningmanage.service.TeamService;
import com.spt.learningmanage.service.TeamMembershipTerminationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 团队接口
 */
@RestController
@RequestMapping("/team")
public class TeamController {

    @Resource
    private TeamService teamService;

    @Resource
    private TeamMembershipTerminationService teamMembershipTerminationService;

    /**
     * 创建团队
     */
    @PostMapping("/create")
    public BaseResponse<TeamCreateVO> createTeam(@RequestBody @Valid TeamCreateRequest request) {
        return ResultUtils.success(teamService.createTeam(request));
    }

    /**
     * 加入团队
     */
    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody @Valid TeamJoinRequest request) {
        teamService.joinTeam(request);
        return ResultUtils.success(true);
    }

    /**
     * 查询我的团队
     */
    @GetMapping("/my")
    public BaseResponse<List<TeamVO>> listMyTeams() {
        return ResultUtils.success(teamService.listMyTeams());
    }

    /**
     * 查询团队成员
     */
    @GetMapping("/{teamId}/members")
    public BaseResponse<List<TeamMemberVO>> listTeamMembers(@PathVariable Long teamId) {
        return ResultUtils.success(teamService.listTeamMembers(teamId));
    }

    /**
     * 修改成员角色
     */
    @PostMapping("/member/role/update")
    public BaseResponse<Boolean> updateMemberRole(@RequestBody @Valid TeamMemberRoleUpdateRequest request) {
        teamService.updateMemberRole(request);
        return ResultUtils.success(true);
    }

    /** 当前登录用户主动退出团队。 */
    @PostMapping("/{teamId}/leave")
    public BaseResponse<TeamMembershipTerminationVO> leaveTeam(
            @PathVariable Long teamId
    ) {
        return ResultUtils.success(
                teamMembershipTerminationService.leaveTeam(teamId)
        );
    }

    /** OWNER/ADMIN 移除团队成员。 */
    @PostMapping("/member/remove")
    public BaseResponse<TeamMembershipTerminationVO> removeMember(
            @RequestBody @Valid TeamMemberRemoveRequest request
    ) {
        return ResultUtils.success(
                teamMembershipTerminationService.removeMember(request)
        );
    }
}

