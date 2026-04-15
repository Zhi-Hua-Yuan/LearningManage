package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.DeleteSourceConstant;
import com.spt.learningmanage.constant.ProjectConstant;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.project.ProjectCreateRequest;
import com.spt.learningmanage.model.dto.project.ProjectQueryRequest;
import com.spt.learningmanage.model.dto.project.ProjectReorderRequest;
import com.spt.learningmanage.model.dto.project.ProjectUpdateRequest;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.project.ProjectVo;
import com.spt.learningmanage.service.ProjectService;
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

    /**
     * 创建项目，返回项目ID。
     */
    @Override
    public Long create(ProjectCreateRequest projectCreateRequest) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (projectCreateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        validateName(projectCreateRequest.getName());
        validateDateRange(projectCreateRequest.getStartDate(), projectCreateRequest.getEndDate());

        Project project = new Project();
        project.setName(projectCreateRequest.getName().trim());
        project.setGoal(projectCreateRequest.getGoal());
        project.setStartDate(projectCreateRequest.getStartDate());
        project.setEndDate(projectCreateRequest.getEndDate());
        project.setStatus(ProjectConstant.STATUS_ACTIVE);
        project.setIsDelete(0);
        project.setUserId(userId);
        project.setOrderNo(getNextOrderNo(userId));

        int rows = projectMapper.insert(project);
        if (rows != 1 || project.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建项目失败");
        }
        return project.getId();
    }

    /**
     * 根据ID查询项目详情。
     */
    @Override
    public ProjectVo getById(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不能为空");
        }
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, id)
                .eq(Project::getUserId, userId)
                .isNull(Project::getDeletedAt);
        Project project = projectMapper.selectOne(wrapper);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return toVo(project);
    }

    /**
     * 分页查询项目列表。
     */
    @Override
    public Page<ProjectVo> list(ProjectQueryRequest projectQueryRequest) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        ProjectQueryRequest validProjectQueryRequest =
                projectQueryRequest == null ? new ProjectQueryRequest() : projectQueryRequest;
        long pageNum = safePageNum(validProjectQueryRequest.getPageNum());
        long pageSize = safePageSize(validProjectQueryRequest.getPageSize());

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Project::getDeletedAt);
        wrapper.eq(Project::getUserId, userId);
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

    /**
     * 更新项目信息。
     */
    @Override
    public void update(ProjectUpdateRequest projectUpdateRequest) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (projectUpdateRequest == null || projectUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不能为空");
        }
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, projectUpdateRequest.getId()).eq(Project::getUserId, userId);
        Project existing = projectMapper.selectOne(wrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        String newName = projectUpdateRequest.getName() != null
                ? projectUpdateRequest.getName().trim() : existing.getName();
        validateName(newName);

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
        update.setGoal(newGoal);
        update.setStatus(newStatus);
        update.setStartDate(newStartDate);
        update.setEndDate(newEndDate);
        update.setUserId(userId);

        int rows = projectMapper.updateById(update);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新项目失败");
        }
    }

    /**
     * 调整项目排序。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void reorder(List<ProjectReorderRequest> reorderRequests) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (reorderRequests == null || reorderRequests.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序列表不能为空");
        }

        Set<Long> idSet = new HashSet<>();
        Set<Integer> orderNoSet = new HashSet<>();
        for (ProjectReorderRequest reorderRequest : reorderRequests) {
            if (reorderRequest == null || reorderRequest.getId() == null || reorderRequest.getId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
            }
            if (reorderRequest.getOrderNo() == null || reorderRequest.getOrderNo() < 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序序号不合法");
            }
            if (!idSet.add(reorderRequest.getId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目列表中存在重复 ID");
            }
            if (!orderNoSet.add(reorderRequest.getOrderNo())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序序号不能重复");
            }
        }

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Project::getId, idSet).eq(Project::getUserId, userId);
        List<Project> existingProjects = projectMapper.selectList(wrapper);
        if (existingProjects.size() != reorderRequests.size()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "存在无权限或不存在的项目");
        }

        for (ProjectReorderRequest reorderRequest : reorderRequests) {
            Project update = new Project();
            update.setId(reorderRequest.getId());
            update.setUserId(userId);
            update.setOrderNo(reorderRequest.getOrderNo());
            int rows = projectMapper.updateById(update);
            if (rows != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新项目排序失败");
            }
        }
    }

    /**
     * 归档项目。
     */
    @Override
    public void archive(List<Long> ids) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 列表不能为空");
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
            }
        }

        // 检查所有项目是否存在
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Project::getId, ids).eq(Project::getUserId, userId);
        List<Project> existingProjects = projectMapper.selectList(wrapper);
        if (existingProjects.size() != ids.size()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 检查项目是否已经归档
        for (Project project : existingProjects) {
            if (Objects.equals(project.getStatus(), ProjectConstant.STATUS_ARCHIVED)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 " + project.getId() + " 已经归档，无法再次归档");
            }
        }

        // 批量更新状态为归档
        LambdaUpdateWrapper<Project> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Project::getId, ids)
                .eq(Project::getUserId, userId)
                .set(Project::getStatus, ProjectConstant.STATUS_ARCHIVED);

        int rows = projectMapper.update(null, updateWrapper);
        if (rows < ids.size()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "归档项目失败");
        }
    }

    /**
     * 删除项目。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不能为空");
        }
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, id).eq(Project::getUserId, userId);
        Project existing = projectMapper.selectOne(wrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (existing.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目已删除，请勿重复操作");
        }

        LocalDateTime deleteTime = LocalDateTime.now();

        LambdaUpdateWrapper<Task> taskDeleteWrapper = new LambdaUpdateWrapper<>();
        taskDeleteWrapper.eq(Task::getUserId, userId)
                .eq(Task::getProjectId, id)
                .eq(Task::getIsDelete, 0)
                .set(Task::getIsDelete, 1)
                .set(Task::getDeleteSource, DeleteSourceConstant.PROJECT_CASCADE)
                .set(Task::getDeletedAt, deleteTime);
        taskMapper.update(null, taskDeleteWrapper);

        LambdaUpdateWrapper<Milestone> milestoneDeleteWrapper = new LambdaUpdateWrapper<>();
        milestoneDeleteWrapper.eq(Milestone::getUserId, userId)
                .eq(Milestone::getProjectId, id)
                .eq(Milestone::getIsDelete, 0)
                .set(Milestone::getIsDelete, 1)
                .set(Milestone::getDeleteSource, DeleteSourceConstant.PROJECT_CASCADE)
                .set(Milestone::getDeletedAt, deleteTime);
        milestoneMapper.update(null, milestoneDeleteWrapper);

        // 软删除：设置 deletedAt
        Project update = new Project();
        update.setId(id);
        update.setDeletedAt(deleteTime);
        update.setUserId(userId);

        int rows = projectMapper.updateById(update);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除项目失败");
        }
    }

    /**
     * 恢复项目。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recover(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不能为空");
        }
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, id).eq(Project::getUserId, userId);
        Project existing = projectMapper.selectOne(wrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (existing.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目未被删除，无法恢复");
        }
        if (existing.getDeletedAt().plusDays(30).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目删除超过30天，无法恢复");
        }

        // 恢复：清空 deletedAt
        LambdaUpdateWrapper<Project> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Project::getId, id)
                .eq(Project::getUserId, userId)
                .set(Project::getDeletedAt, null);

        int rows = projectMapper.update(null, updateWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "恢复项目失败");
        }

        taskMapper.recoverByProjectId(userId, id);
        milestoneMapper.recoverByProjectId(userId, id);
    }

    /**
     * 将实体转换为VO。
     */
    private ProjectVo toVo(Project project) {
        ProjectVo vo = new ProjectVo();
        BeanUtils.copyProperties(project, vo);
        return vo;
    }

    /**
     * 校验项目名称。
     */
    private void validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_EMPTY);
        }
        if (name.length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目名称长度不能超过100");
        }
    }

    /**
     * 校验日期区间。
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "结束日期不能早于开始日期");
        }
    }

    /**
     * 校验项目状态。
     */
    private void validateStatus(Integer status) {
        if (!Objects.equals(status, ProjectConstant.STATUS_ACTIVE)
                && !Objects.equals(status, ProjectConstant.STATUS_ARCHIVED)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目状态不合法");
        }
    }

    /**
     * 规范化页码。
     */
    private long safePageNum(Long pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1L;
        }
        return pageNum;
    }

    /**
     * 规范化每页条数。
     */
    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100L);
    }

    /**
     * 获取当前用户下新的排序号。
     */
    private Integer getNextOrderNo(Long userId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getUserId, userId)
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getOrderNo)
                .last("LIMIT 1");
        Project lastProject = projectMapper.selectOne(wrapper);
        if (lastProject == null || lastProject.getOrderNo() == null) {
            return 0;
        }
        return lastProject.getOrderNo() + 1;
    }
}
