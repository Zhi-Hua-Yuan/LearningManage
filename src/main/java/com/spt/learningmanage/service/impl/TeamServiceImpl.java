package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.team.TeamCreateRequest;
import com.spt.learningmanage.model.dto.team.TeamJoinRequest;
import com.spt.learningmanage.model.dto.team.TeamMemberRoleUpdateRequest;
import com.spt.learningmanage.model.dto.team.TeamOwnerTransferRequest;
import com.spt.learningmanage.model.dto.team.TeamUpdateRequest;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.vo.team.TeamCreateVO;
import com.spt.learningmanage.model.vo.team.TeamDissolutionCheckVO;
import com.spt.learningmanage.model.vo.team.TeamDissolutionVO;
import com.spt.learningmanage.model.vo.team.TeamInviteVO;
import com.spt.learningmanage.model.vo.team.TeamMemberVO;
import com.spt.learningmanage.model.vo.team.TeamOwnershipTransferVO;
import com.spt.learningmanage.model.vo.team.TeamVO;
import com.spt.learningmanage.service.TeamService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 团队服务实现
 */
@Service
public class TeamServiceImpl implements TeamService {

    private static final int TEAM_NAME_MAX_LENGTH = 60;
    private static final int TEAM_DESCRIPTION_MAX_LENGTH = 200;
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_RETRY = 10;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private PermissionService permissionService;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private WeeklyReviewMapper weeklyReviewMapper;

    @Resource
    private KnowledgeIndexEventPublisher knowledgeIndexEventPublisher;

    @Resource
    private BusinessDataVersionService businessDataVersionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamCreateVO createTeam(TeamCreateRequest request) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        User currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "当前用户不存在");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }

        String teamName = StrUtil.trim(request.getName());
        String description = StrUtil.trim(request.getDescription());
        validateTeamName(teamName);
        validateDescription(description);

        String inviteCode = generateUniqueInviteCode();

        Team team = new Team();
        team.setName(teamName);
        team.setDescription(description);
        team.setOwnerId(userId);
        team.setInviteCode(inviteCode);
        team.setIsDelete(0);
        int teamInsertRows = teamMapper.insert(team);
        if (teamInsertRows != 1 || team.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建团队失败");
        }

        TeamMember teamMember = new TeamMember();
        teamMember.setTeamId(team.getId());
        teamMember.setUserId(userId);
        teamMember.setRole(TeamRoleEnum.OWNER.getValue());
        teamMember.setIsDelete(0);
        int memberInsertRows = teamMemberMapper.insert(teamMember);
        if (memberInsertRows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建团队成员关系失败");
        }

        TeamCreateVO teamCreateVO = new TeamCreateVO();
        teamCreateVO.setTeamId(team.getId());
        teamCreateVO.setInviteCode(inviteCode);
        return teamCreateVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinTeam(TeamJoinRequest request) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }

        String inviteCode = StrUtil.trim(request.getInviteCode());
        if (inviteCode != null) {
            inviteCode = inviteCode.toUpperCase(Locale.ROOT);
        }
        if (StrUtil.isBlank(inviteCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邀请码不能为空");
        }

        Team team = teamMapper.selectActiveByInviteCodeForUpdate(inviteCode);
        if (team == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "团队不存在");
        }

        // 这里用原生 SQL 查询，避免逻辑删除插件过滤掉历史成员记录。
        Integer isDelete = jdbcTemplate.query(
                "SELECT is_delete FROM team_member WHERE team_id = ? AND user_id = ? LIMIT 1",
                rs -> rs.next() ? rs.getInt("is_delete") : null,
                team.getId(), userId
        );

        if (isDelete != null) {
            if (isDelete == 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "你已加入该团队");
            }
            int updateRows = jdbcTemplate.update(
                    "UPDATE team_member SET role = ?, is_delete = 0, deleted_at = NULL WHERE team_id = ? AND user_id = ? AND is_delete = 1",
                    TeamRoleEnum.MEMBER.getValue(), team.getId(), userId
            );
            if (updateRows != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "加入团队失败");
            }
            publishReviewAccessRestored(team.getId(), userId);
            return;
        }

        TeamMember teamMember = new TeamMember();
        teamMember.setTeamId(team.getId());
        teamMember.setUserId(userId);
        teamMember.setRole(TeamRoleEnum.MEMBER.getValue());
        teamMember.setIsDelete(0);
        int rows = teamMemberMapper.insert(teamMember);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "加入团队失败");
        }
        publishReviewAccessRestored(team.getId(), userId);
    }

    private void publishReviewAccessRestored(Long teamId, Long userId) {
        if (businessDataVersionService != null) {
            businessDataVersionService.incrementTeam(teamId);
        }
        if (knowledgeIndexEventPublisher == null || projectMapper == null || weeklyReviewMapper == null) {
            return;
        }
        List<Long> projectIds = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, teamId)
                        .eq(Project::getIsDelete, 0)
                        .isNull(Project::getDeletedAt))
                .stream().map(Project::getId).toList();
        if (projectIds.isEmpty()) {
            return;
        }
        List<Long> reviewIds = weeklyReviewMapper.selectList(new LambdaQueryWrapper<WeeklyReview>()
                        .eq(WeeklyReview::getUserId, userId)
                        .in(WeeklyReview::getFocusProjectId, projectIds))
                .stream().map(WeeklyReview::getId).toList();
        knowledgeIndexEventPublisher.publishAll(KnowledgeSourceTypeEnum.WEEKLY_REVIEW,
                reviewIds, KnowledgeEventTypeEnum.ACCESS_CHANGED);
    }

    @Override
    public List<TeamVO> listMyTeams() {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);

        List<TeamMember> memberList = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getIsDelete, 0)
                .orderByDesc(TeamMember::getCreateTime));
        if (memberList == null || memberList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> teamIdSet = memberList.stream()
                .map(TeamMember::getTeamId)
                .filter(teamId -> teamId != null && teamId > 0)
                .collect(Collectors.toSet());
        if (teamIdSet.isEmpty()) {
            return Collections.emptyList();
        }

        List<Team> teamList = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .in(Team::getId, teamIdSet)
                .eq(Team::getIsDelete, 0));
        if (teamList == null || teamList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Team> teamMap = new HashMap<>();
        for (Team team : teamList) {
            teamMap.put(team.getId(), team);
        }

        List<TeamVO> result = new ArrayList<>();
        for (TeamMember member : memberList) {
            Team team = teamMap.get(member.getTeamId());
            if (team == null) {
                continue;
            }
            TeamVO teamVO = new TeamVO();
            teamVO.setId(team.getId());
            teamVO.setName(team.getName());
            teamVO.setDescription(team.getDescription());
            teamVO.setOwnerId(team.getOwnerId());
            teamVO.setRole(member.getRole());
            teamVO.setCreateTime(team.getCreateTime());
            result.add(teamVO);
        }
        return result;
    }

    @Override
    public List<TeamMemberVO> listTeamMembers(Long teamId) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        permissionService.requireTeamMemberList(userId, teamId);
        getValidTeamById(teamId);

        List<TeamMember> memberList = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsDelete, 0)
                .orderByAsc(TeamMember::getCreateTime));
        if (memberList == null || memberList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIdSet = memberList.stream()
                .map(TeamMember::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (userIdSet.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> userList = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, userIdSet)
                .eq(User::getIsDelete, 0));
        Map<Long, User> userMap = new HashMap<>();
        if (userList != null) {
            for (User user : userList) {
                userMap.put(user.getId(), user);
            }
        }

        List<TeamMemberVO> result = new ArrayList<>();
        for (TeamMember member : memberList) {
            TeamMemberVO vo = new TeamMemberVO();
            vo.setUserId(member.getUserId());
            vo.setRole(member.getRole());
            vo.setJoinTime(member.getCreateTime());
            User user = userMap.get(member.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(TeamMemberRoleUpdateRequest request) {
        Long userId = getLoginUserId();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }

        Long teamId = request.getTeamId();
        Long targetUserId = request.getTargetUserId();
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "团队ID不合法");
        }
        if (targetUserId == null || targetUserId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标用户ID不合法");
        }

        String role = StrUtil.trim(request.getRole());
        if (role != null) {
            role = role.toUpperCase(Locale.ROOT);
        }
        if (!TeamRoleEnum.ADMIN.getValue().equals(role)
                && !TeamRoleEnum.MEMBER.getValue().equals(role)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标角色不合法");
        }

        permissionService.requireTeamMemberRoleUpdate(userId, teamId, targetUserId);
        Team team = lockActiveTeam(teamId);
        List<TeamMember> lockedMembers = teamMemberMapper.selectActiveMembersForUpdate(
                teamId, List.of(userId, targetUserId));
        Map<Long, TeamMember> byUserId = lockedMembers.stream()
                .collect(Collectors.toMap(TeamMember::getUserId, member -> member));
        TeamMember actor = byUserId.get(userId);
        TeamMember targetMember = byUserId.get(targetUserId);
        requireOwner(team, actor, userId);
        if (targetMember == null || TeamRoleEnum.isOwner(targetMember.getRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不能修改团队拥有者角色");
        }

        if (role.equals(targetMember.getRole())) {
            return;
        }

        targetMember.setRole(role);
        int rows = teamMemberMapper.updateById(targetMember);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "修改成员角色失败");
        }
        if (businessDataVersionService != null) {
            businessDataVersionService.incrementTeam(teamId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamVO updateTeam(TeamUpdateRequest request) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        String name = StrUtil.trim(request.getName());
        String description = StrUtil.trim(request.getDescription());
        validateTeamName(name);
        validateDescription(description);

        Team team = lockActiveTeam(request.getTeamId());
        TeamMember actor = lockActorMembership(team.getId(), userId);
        TeamRoleEnum role = requireKnownRole(actor);
        if (role != TeamRoleEnum.OWNER && role != TeamRoleEnum.ADMIN) {
            throw new PermissionDeniedException();
        }

        team.setName(name);
        team.setDescription(description);
        if (teamMapper.updateById(team) != 1) {
            throw new BusinessException(ErrorCode.TEAM_STATE_CONFLICT, "团队资料已发生变化，请刷新后重试");
        }
        incrementTeamVersion(team.getId());
        return toTeamVO(team, role.getValue());
    }

    @Override
    public TeamInviteVO getInvite(Long teamId) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        Team team = getValidTeamById(teamId);
        TeamMember actor = requireValidTeamMember(teamId, userId);
        requireOwner(team, actor, userId);
        return new TeamInviteVO(team.getId(), team.getInviteCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamInviteVO regenerateInvite(Long teamId) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        Team team = lockActiveTeam(teamId);
        TeamMember actor = lockActorMembership(teamId, userId);
        requireOwner(team, actor, userId);

        String inviteCode = generateUniqueInviteCode();
        team.setInviteCode(inviteCode);
        if (teamMapper.updateById(team) != 1) {
            throw new BusinessException(ErrorCode.TEAM_STATE_CONFLICT, "邀请码刷新失败，请重试");
        }
        incrementTeamVersion(teamId);
        return new TeamInviteVO(teamId, inviteCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamOwnershipTransferVO transferOwnership(TeamOwnerTransferRequest request) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        if (request == null || request.getTeamId() == null || request.getTargetUserId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        if (Objects.equals(userId, request.getTargetUserId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能将所有权转让给自己");
        }

        Team team = lockActiveTeam(request.getTeamId());
        List<TeamMember> members = teamMemberMapper.selectActiveMembersForUpdate(
                team.getId(), List.of(userId, request.getTargetUserId()));
        Map<Long, TeamMember> byUserId = members.stream()
                .collect(Collectors.toMap(TeamMember::getUserId, member -> member));
        TeamMember actor = byUserId.get(userId);
        TeamMember target = byUserId.get(request.getTargetUserId());
        requireOwner(team, actor, userId);
        if (target == null || requireKnownRole(target) == TeamRoleEnum.OWNER) {
            throw new BusinessException(ErrorCode.TEAM_STATE_CONFLICT, "目标成员已不可用于所有权转让");
        }

        actor.setRole(TeamRoleEnum.ADMIN.getValue());
        target.setRole(TeamRoleEnum.OWNER.getValue());
        team.setOwnerId(target.getUserId());
        if (teamMemberMapper.updateById(actor) != 1
                || teamMemberMapper.updateById(target) != 1
                || teamMapper.updateById(team) != 1) {
            throw new BusinessException(ErrorCode.TEAM_STATE_CONFLICT, "团队所有权已发生变化，请刷新后重试");
        }
        incrementTeamVersion(team.getId());

        TeamOwnershipTransferVO result = new TeamOwnershipTransferVO();
        result.setTeamId(team.getId());
        result.setPreviousOwnerUserId(userId);
        result.setNewOwnerUserId(target.getUserId());
        result.setTransferredAt(LocalDateTime.now());
        return result;
    }

    @Override
    public TeamDissolutionCheckVO checkDissolution(Long teamId) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        Team team = getValidTeamById(teamId);
        TeamMember actor = requireValidTeamMember(teamId, userId);
        requireOwner(team, actor, userId);
        return buildDissolutionCheck(teamId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamDissolutionVO dissolveTeam(Long teamId) {
        Long userId = getLoginUserId();
        permissionService.requireActiveActor(userId);
        Team team = lockActiveTeam(teamId);
        List<TeamMember> members = teamMemberMapper.selectAllActiveMembersForUpdate(teamId);
        TeamMember actor = members.stream()
                .filter(member -> Objects.equals(member.getUserId(), userId))
                .findFirst().orElse(null);
        requireOwner(team, actor, userId);

        TeamDissolutionCheckVO check = buildDissolutionCheck(teamId);
        if (!check.isCanDissolve()) {
            throw new BusinessException(
                    ErrorCode.TEAM_HAS_BUSINESS_DATA,
                    "团队仍有 " + check.getActiveProjectCount() + " 个有效项目和 "
                            + check.getSharedReviewCount() + " 条共享周报，暂不能解散"
            );
        }

        LocalDateTime dissolvedAt = LocalDateTime.now();
        if (teamMemberMapper.deactivateAllActiveMemberships(teamId, dissolvedAt) != members.size()
                || teamMapper.deactivateTeamCas(teamId, dissolvedAt) != 1) {
            throw new BusinessException(ErrorCode.TEAM_STATE_CONFLICT, "团队状态已发生变化，请刷新后重试");
        }
        incrementTeamVersion(teamId);

        TeamDissolutionVO result = new TeamDissolutionVO();
        result.setTeamId(teamId);
        result.setDissolvedAt(dissolvedAt);
        result.setRemovedMemberCount(members.size());
        return result;
    }

    private Team lockActiveTeam(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "团队ID不合法");
        }
        Team team = teamMapper.selectActiveByIdForUpdate(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "团队不存在");
        }
        return team;
    }

    private TeamMember lockActorMembership(Long teamId, Long userId) {
        List<TeamMember> members = teamMemberMapper.selectActiveMembersForUpdate(teamId, List.of(userId));
        if (members.size() != 1) {
            throw new PermissionDeniedException();
        }
        return members.get(0);
    }

    private TeamRoleEnum requireKnownRole(TeamMember member) {
        TeamRoleEnum role = member == null ? null : TeamRoleEnum.fromValue(member.getRole());
        if (role == null) {
            throw new PermissionDeniedException();
        }
        return role;
    }

    private void requireOwner(Team team, TeamMember actor, Long userId) {
        if (team == null || actor == null || !Objects.equals(team.getOwnerId(), userId)
                || requireKnownRole(actor) != TeamRoleEnum.OWNER) {
            throw new PermissionDeniedException();
        }
    }

    private TeamDissolutionCheckVO buildDissolutionCheck(Long teamId) {
        Long projectCount = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .eq(Project::getTeamId, teamId)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt));
        Long reviewCount = weeklyReviewMapper.selectCount(new LambdaQueryWrapper<WeeklyReview>()
                .eq(WeeklyReview::getTeamId, teamId)
                .eq(WeeklyReview::getVisibilityScope, "TEAM"));
        TeamDissolutionCheckVO result = new TeamDissolutionCheckVO();
        result.setTeamId(teamId);
        result.setActiveProjectCount(projectCount == null ? 0 : projectCount);
        result.setSharedReviewCount(reviewCount == null ? 0 : reviewCount);
        result.setCanDissolve(result.getActiveProjectCount() == 0 && result.getSharedReviewCount() == 0);
        return result;
    }

    private TeamVO toTeamVO(Team team, String role) {
        TeamVO result = new TeamVO();
        result.setId(team.getId());
        result.setName(team.getName());
        result.setDescription(team.getDescription());
        result.setOwnerId(team.getOwnerId());
        result.setRole(role);
        result.setCreateTime(team.getCreateTime());
        return result;
    }

    private void incrementTeamVersion(Long teamId) {
        if (businessDataVersionService != null) {
            businessDataVersionService.incrementTeam(teamId);
        }
    }

    private void validateTeamName(String name) {
        String teamName = StrUtil.trim(name);
        if (StrUtil.isBlank(teamName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "团队名称不能为空");
        }
        if (teamName.length() > TEAM_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "团队名称长度不能超过60个字符");
        }
    }

    private void validateDescription(String description) {
        String trimDescription = StrUtil.trim(description);
        if (trimDescription != null && trimDescription.length() > TEAM_DESCRIPTION_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "团队描述长度不能超过200个字符");
        }
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < INVITE_CODE_MAX_RETRY; i++) {
            String inviteCode = randomInviteCode();
            Long count = teamMapper.selectCount(new LambdaQueryWrapper<Team>()
                    .eq(Team::getInviteCode, inviteCode));
            if (count == null || count == 0) {
                return inviteCode;
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成邀请码失败，请稍后重试");
    }

    private String randomInviteCode() {
        return RandomUtil.randomString(INVITE_CODE_CHARS, INVITE_CODE_LENGTH).toUpperCase(Locale.ROOT);
    }

    private Team getValidTeamById(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "团队ID不合法");
        }
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getId, teamId)
                .eq(Team::getIsDelete, 0));
        if (team == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "团队不存在");
        }
        return team;
    }

    private TeamMember getValidTeamMemberOrNull(Long teamId, Long userId) {
        if (teamId == null || teamId <= 0 || userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getIsDelete, 0));
    }

    private TeamMember requireValidTeamMember(Long teamId, Long userId) {
        TeamMember teamMember = getValidTeamMemberOrNull(teamId, userId);
        if (teamMember == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你不是该团队成员");
        }
        return teamMember;
    }

    private Long getLoginUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        return userId;
    }
}
