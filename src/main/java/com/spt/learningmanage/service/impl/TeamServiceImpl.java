package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.team.TeamCreateRequest;
import com.spt.learningmanage.model.dto.team.TeamJoinRequest;
import com.spt.learningmanage.model.dto.team.TeamMemberRoleUpdateRequest;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.vo.team.TeamCreateVO;
import com.spt.learningmanage.model.vo.team.TeamMemberVO;
import com.spt.learningmanage.model.vo.team.TeamVO;
import com.spt.learningmanage.service.TeamService;
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
import java.util.Set;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamCreateVO createTeam(TeamCreateRequest request) {
        Long userId = getLoginUserId();
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

        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getInviteCode, inviteCode)
                .eq(Team::getIsDelete, 0));
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
    }

    @Override
    public List<TeamVO> listMyTeams() {
        Long userId = getLoginUserId();

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
        getValidTeamById(teamId);
        requireValidTeamMember(teamId, userId);

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

        getValidTeamById(teamId);
        validateOwner(teamId, userId);

        TeamMember targetMember = requireValidTeamMember(teamId, targetUserId);
        if (TeamRoleEnum.isOwner(targetMember.getRole())) {
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

    private void validateOwner(Long teamId, Long userId) {
        TeamMember teamMember = requireValidTeamMember(teamId, userId);
        if (!TeamRoleEnum.isOwner(teamMember.getRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅团队拥有者可执行该操作");
        }
    }

    private Long getLoginUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        return userId;
    }
}
