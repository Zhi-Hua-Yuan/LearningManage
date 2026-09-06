package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.DeleteSourceConstant;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.ProjectConstant;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.project.ProjectCreateRequest;
import com.spt.learningmanage.model.dto.project.ProjectQueryRequest;
import com.spt.learningmanage.model.dto.project.ProjectReorderRequest;
import com.spt.learningmanage.model.dto.project.ProjectUpdateRequest;
import com.spt.learningmanage.model.dto.project.TeamProjectCreateRequest;
import com.spt.learningmanage.model.dto.project.TeamProjectQueryRequest;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.vo.project.ProjectVo;
import com.spt.learningmanage.service.ProjectService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private MilestoneMapper milestoneMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private PermissionService permissionService;

    @Resource
    private WeeklyReviewMapper weeklyReviewMapper;

    @Resource
    private KnowledgeIndexEventPublisher knowledgeIndexEventPublisher;

    @Resource
    private BusinessDataVersionService businessDataVersionService;

    @Override
    public Long create(ProjectCreateRequest projectCreateRequest) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        permissionService.requireActiveActor(userId);
        if (projectCreateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        validateName(projectCreateRequest.getName());
        validateIcon(projectCreateRequest.getIcon());
        String color = normalizeColor(projectCreateRequest.getColor());
        validateColor(color);
        validateDateRange(projectCreateRequest.getStartDate(), projectCreateRequest.getEndDate());

        Project project = new Project();
        project.setName(projectCreateRequest.getName().trim());
        project.setIcon(projectCreateRequest.getIcon());
        project.setColor(color);
        project.setGoal(projectCreateRequest.getGoal());
        project.setStartDate(projectCreateRequest.getStartDate());
        project.setEndDate(projectCreateRequest.getEndDate());
        project.setStatus(ProjectConstant.STATUS_ACTIVE);
        project.setIsDelete(0);
        project.setUserId(userId);
        project.setTeamId(null);
        project.setOrderNo(getNextPersonalProjectOrderNo(userId));

        int rows = projectMapper.insert(project);
        if (rows != 1 || project.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作失败");
        }
        return project.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTeamProject(TeamProjectCreateRequest teamProjectCreateRequest) {
        // 团队项目创建：OWNER/ADMIN 可创建，MEMBER 不可创建
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (teamProjectCreateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        if (teamProjectCreateRequest.getTeamId() == null || teamProjectCreateRequest.getTeamId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }

        permissionService.requireTeamManageProject(userId, teamProjectCreateRequest.getTeamId());
        Team team = getValidTeamById(teamProjectCreateRequest.getTeamId());

        validateName(teamProjectCreateRequest.getName());
        validateIcon(teamProjectCreateRequest.getIcon());
        String color = normalizeColor(teamProjectCreateRequest.getColor());
        validateColor(color);
        validateDateRange(teamProjectCreateRequest.getStartDate(), teamProjectCreateRequest.getEndDate());

        Project project = new Project();
        project.setName(teamProjectCreateRequest.getName().trim());
        project.setGoal(teamProjectCreateRequest.getGoal());
        project.setIcon(teamProjectCreateRequest.getIcon());
        project.setColor(color);
        project.setStartDate(teamProjectCreateRequest.getStartDate());
        project.setEndDate(teamProjectCreateRequest.getEndDate());
        project.setStatus(ProjectConstant.STATUS_ACTIVE);
        project.setIsDelete(0);
        project.setUserId(userId);
        project.setTeamId(team.getId());
        project.setOrderNo(getNextTeamProjectOrderNo(team.getId()));

        int rows = projectMapper.insert(project);
        if (rows != 1 || project.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作失败");
        }
        if (businessDataVersionService != null) {
            businessDataVersionService.incrementTeam(team.getId());
        }
        return project.getId();
    }

    @Override
    public ProjectVo getById(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        permissionService.requireProjectView(userId, id);
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, id)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt);
        Project project = projectMapper.selectOne(wrapper);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return toVo(project);
    }

    @Override
    public Page<ProjectVo> list(ProjectQueryRequest projectQueryRequest) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        permissionService.requireActiveActor(userId);
        ProjectQueryRequest validProjectQueryRequest =
                projectQueryRequest == null ? new ProjectQueryRequest() : projectQueryRequest;
        long pageNum = safePageNum(validProjectQueryRequest.getPageNum());
        long pageSize = safePageSize(validProjectQueryRequest.getPageSize());

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Project::getDeletedAt);
        wrapper.eq(Project::getUserId, userId);
        wrapper.isNull(Project::getTeamId);
        if (validProjectQueryRequest.getStatus() != null) {
            wrapper.eq(Project::getStatus, validProjectQueryRequest.getStatus());
        }
        if (StringUtils.hasText(validProjectQueryRequest.getKeyword())) {
            wrapper.like(Project::getName, validProjectQueryRequest.getKeyword());
        }
        wrapper.orderByAsc(Project::getOrderNo).orderByDesc(Project::getCreateTime);

        Page<Project> page = new Page<>(pageNum, pageSize);
        Page<Project> resultPage = projectMapper.selectPage(page, wrapper);
        Page<ProjectVo> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::toVo).toList());
        return voPage;
    }

    @Override
    public Page<ProjectVo> listTeamProjects(TeamProjectQueryRequest teamProjectQueryRequest) {
        // 团队项目列表：团队成员均可查看
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        TeamProjectQueryRequest validRequest = teamProjectQueryRequest == null
                ? new TeamProjectQueryRequest()
                : teamProjectQueryRequest;
        if (validRequest.getTeamId() == null || validRequest.getTeamId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }

        permissionService.requireTeamView(userId, validRequest.getTeamId());
        Team team = getValidTeamById(validRequest.getTeamId());

        long pageNum = safePageNum(validRequest.getPageNum());
        long pageSize = safePageSize(validRequest.getPageSize());

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getTeamId, team.getId())
                .isNull(Project::getDeletedAt);
        if (validRequest.getStatus() != null) {
            validateStatus(validRequest.getStatus());
            wrapper.eq(Project::getStatus, validRequest.getStatus());
        }
        String keyword = validRequest.getKeyword();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Project::getName, keyword.trim());
        }
        wrapper.orderByAsc(Project::getOrderNo).orderByDesc(Project::getCreateTime);

        Page<Project> page = new Page<>(pageNum, pageSize);
        Page<Project> resultPage = projectMapper.selectPage(page, wrapper);
        Page<ProjectVo> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::toVo).toList());
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ProjectUpdateRequest projectUpdateRequest) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (projectUpdateRequest == null || projectUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        permissionService.requireProjectManage(userId, projectUpdateRequest.getId());
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, projectUpdateRequest.getId())
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt);
        Project existing = projectMapper.selectOne(wrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        String newName = projectUpdateRequest.getName() != null
                ? projectUpdateRequest.getName().trim() : existing.getName();
        validateName(newName);

        String newIcon = projectUpdateRequest.getIcon() != null
                ? projectUpdateRequest.getIcon() : existing.getIcon();
        validateIcon(newIcon);

        String newColor = projectUpdateRequest.getColor() != null
                ? normalizeColor(projectUpdateRequest.getColor()) : normalizeColor(existing.getColor());
        validateColor(newColor);

        String newGoal = projectUpdateRequest.getGoal() != null
                ? projectUpdateRequest.getGoal() : existing.getGoal();
        Integer newStatus = projectUpdateRequest.getStatus() != null
                ? projectUpdateRequest.getStatus() : existing.getStatus();
        validateStatus(newStatus);

        LocalDate newStartDate = projectUpdateRequest.getStartDate() != null
                ? projectUpdateRequest.getStartDate() : existing.getStartDate();
        LocalDate newEndDate = projectUpdateRequest.getEndDate() != null
                ? projectUpdateRequest.getEndDate() : existing.getEndDate();
        validateDateRange(newStartDate, newEndDate);

        Project update = new Project();
        update.setId(projectUpdateRequest.getId());
        update.setName(newName);
        update.setIcon(newIcon);
        update.setColor(newColor);
        update.setGoal(newGoal);
        update.setStatus(newStatus);
        update.setStartDate(newStartDate);
        update.setEndDate(newEndDate);
        int rows = projectMapper.updateById(update);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作失败");
        }
        bumpProject(existing.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void reorder(List<ProjectReorderRequest> reorderRequests) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (reorderRequests == null || reorderRequests.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }

        Set<Long> idSet = new HashSet<>();
        Set<Integer> orderNoSet = new HashSet<>();
        for (ProjectReorderRequest reorderRequest : reorderRequests) {
            if (reorderRequest == null || reorderRequest.getId() == null || reorderRequest.getId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
            }
            if (reorderRequest.getOrderNo() == null || reorderRequest.getOrderNo() < 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
            }
            if (!idSet.add(reorderRequest.getId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
            }
            if (!orderNoSet.add(reorderRequest.getOrderNo())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
            }
        }

        Map<Long, ProjectAccessScope> scopes = permissionService.resolveProjectScopes(userId, idSet);
        if (scopes.size() != idSet.size() || scopes.values().stream().anyMatch(scope -> !scope.canManage())) {
            throw new com.spt.learningmanage.exception.PermissionDeniedException();
        }

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Project::getId, idSet)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt);
        List<Project> existingProjects = projectMapper.selectList(wrapper);
        if (existingProjects.size() != reorderRequests.size()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在");
        }

        for (ProjectReorderRequest reorderRequest : reorderRequests) {
            Project update = new Project();
            update.setId(reorderRequest.getId());
            update.setOrderNo(reorderRequest.getOrderNo());
            int rows = projectMapper.updateById(update);
            if (rows != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作失败");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(List<Long> ids) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
            }
        }

        Map<Long, ProjectAccessScope> scopes = permissionService.resolveProjectScopes(userId, ids);
        if (scopes.size() != new HashSet<>(ids).size()
                || scopes.values().stream().anyMatch(scope -> !scope.canManage())) {
            throw new com.spt.learningmanage.exception.PermissionDeniedException();
        }

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Project::getId, ids)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt);
        List<Project> existingProjects = projectMapper.selectList(wrapper);
        if (existingProjects.size() != ids.size()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        for (Project project : existingProjects) {
            if (Objects.equals(project.getStatus(), ProjectConstant.STATUS_ARCHIVED)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
            }
        }

        LambdaUpdateWrapper<Project> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Project::getId, ids)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt)
                .set(Project::getStatus, ProjectConstant.STATUS_ARCHIVED);

        int rows = projectMapper.update(null, updateWrapper);
        if (rows < ids.size()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作失败");
        }
        existingProjects.forEach(project -> bumpProject(project.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        permissionService.requireProjectManage(userId, id);
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, id)
                .eq(Project::getIsDelete, 0);
        Project existing = projectMapper.selectOne(wrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (existing.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }

        LocalDateTime deleteTime = LocalDateTime.now();

        List<Long> affectedTaskIds = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, id)
                        .eq(Task::getIsDelete, 0))
                .stream().map(Task::getId).toList();
        List<Long> affectedReviewIds = reviewIdsForProject(id);

        LambdaUpdateWrapper<Task> taskDeleteWrapper = new LambdaUpdateWrapper<>();
        taskDeleteWrapper.eq(Task::getProjectId, id)
                .eq(Task::getIsDelete, 0)
                .set(Task::getIsDelete, 1)
                .set(Task::getDeleteSource, DeleteSourceConstant.PROJECT_CASCADE)
                .set(Task::getDeletedAt, deleteTime);
        taskMapper.update(null, taskDeleteWrapper);

        LambdaUpdateWrapper<Milestone> milestoneDeleteWrapper = new LambdaUpdateWrapper<>();
        milestoneDeleteWrapper.eq(Milestone::getProjectId, id)
                .eq(Milestone::getIsDelete, 0)
                .set(Milestone::getIsDelete, 1)
                .set(Milestone::getDeleteSource, DeleteSourceConstant.PROJECT_CASCADE)
                .set(Milestone::getDeletedAt, deleteTime);
        milestoneMapper.update(null, milestoneDeleteWrapper);

        LambdaUpdateWrapper<Project> projectDeleteWrapper = new LambdaUpdateWrapper<>();
        projectDeleteWrapper.eq(Project::getId, id)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt)
                .set(Project::getDeletedAt, deleteTime);

        int rows = projectMapper.update(null, projectDeleteWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作失败");
        }
        publishTasks(affectedTaskIds, KnowledgeEventTypeEnum.SOURCE_DELETED);
        publishReviews(affectedReviewIds, KnowledgeEventTypeEnum.ACCESS_CHANGED);
        bumpProject(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recover(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        permissionService.requireProjectRecover(userId, id);
        Project existing = projectMapper.selectDeletedById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (existing.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        if (existing.getDeletedAt().plusDays(30).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }

        LambdaUpdateWrapper<Project> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Project::getId, id)
                .in(Project::getIsDelete, List.of(0, 1))
                .isNotNull(Project::getDeletedAt)
                .set(Project::getIsDelete, 0)
                .set(Project::getDeletedAt, null);

        int rows = projectMapper.update(null, updateWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作失败");
        }

        taskMapper.recoverByProjectId(userId, id);
        milestoneMapper.recoverByProjectId(userId, id);
        List<Long> recoveredTaskIds = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, id)
                        .eq(Task::getIsDelete, 0))
                .stream().map(Task::getId).toList();
        publishTasks(recoveredTaskIds, KnowledgeEventTypeEnum.SOURCE_CHANGED);
        publishReviews(reviewIdsForProject(id), KnowledgeEventTypeEnum.ACCESS_CHANGED);
        bumpProject(id);
    }

    private List<Long> reviewIdsForProject(Long projectId) {
        if (weeklyReviewMapper == null) {
            return List.of();
        }
        return weeklyReviewMapper.selectList(new LambdaQueryWrapper<WeeklyReview>()
                        .eq(WeeklyReview::getFocusProjectId, projectId))
                .stream().map(WeeklyReview::getId).toList();
    }

    private void publishTasks(List<Long> ids, KnowledgeEventTypeEnum eventType) {
        if (knowledgeIndexEventPublisher != null && ids != null && !ids.isEmpty()) {
            knowledgeIndexEventPublisher.publishAll(KnowledgeSourceTypeEnum.TASK, ids, eventType);
        }
    }

    private void publishReviews(List<Long> ids, KnowledgeEventTypeEnum eventType) {
        if (knowledgeIndexEventPublisher != null && ids != null && !ids.isEmpty()) {
            knowledgeIndexEventPublisher.publishAll(KnowledgeSourceTypeEnum.WEEKLY_REVIEW, ids, eventType);
        }
    }

    private void bumpProject(Long projectId) {
        if (businessDataVersionService != null) {
            businessDataVersionService.incrementProjectAndOwningTeam(projectId);
        }
    }

    private ProjectVo toVo(Project project) {
        ProjectVo vo = new ProjectVo();
        BeanUtils.copyProperties(project, vo);
        return vo;
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_EMPTY);
        }
        if (name.length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
    }

    private void validateStatus(Integer status) {
        if (!Objects.equals(status, ProjectConstant.STATUS_ACTIVE)
                && !Objects.equals(status, ProjectConstant.STATUS_ARCHIVED)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
    }

    private void validateIcon(String icon) {
        if (icon != null && icon.length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
    }

    private void validateColor(String color) {
        if (color != null && !color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
    }

    private String normalizeColor(String color) {
        if (!StringUtils.hasText(color)) {
            return null;
        }
        return color.trim();
    }

    private long safePageNum(Long pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1L;
        }
        return pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100L);
    }

    private long safePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1L;
        }
        return pageNum.longValue();
    }

    private long safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize.longValue(), 100L);
    }

    private Integer getNextPersonalProjectOrderNo(Long userId) {
        // 个人项目排序号按“当前用户 + teamId 为空”维度递增
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getUserId, userId)
                .isNull(Project::getTeamId)
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getOrderNo)
                .last("LIMIT 1");
        Project lastProject = projectMapper.selectOne(wrapper);
        if (lastProject == null || lastProject.getOrderNo() == null) {
            return 0;
        }
        return lastProject.getOrderNo() + 1;
    }

    private Integer getNextTeamProjectOrderNo(Long teamId) {
        // 团队项目排序号按 teamId 维度递增
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getTeamId, teamId)
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getOrderNo)
                .last("LIMIT 1");
        Project lastProject = projectMapper.selectOne(wrapper);
        if (lastProject == null || lastProject.getOrderNo() == null) {
            return 0;
        }
        return lastProject.getOrderNo() + 1;
    }

    private Team getValidTeamById(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getId, teamId)
                .eq(Team::getIsDelete, 0));
        if (team == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资源不存在");
        }
        return team;
    }

}


