package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.model.dto.milestone.MilestoneCreateRequest;
import com.spt.learningmanage.model.dto.milestone.MilestoneQueryRequest;
import com.spt.learningmanage.model.dto.milestone.MilestoneUpdateRequest;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.vo.MilestoneVo;
import com.spt.learningmanage.service.MilestoneService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MilestoneServiceImpl implements MilestoneService {

    private static final BigDecimal MIN_PROGRESS = BigDecimal.ZERO;
    private static final BigDecimal MAX_PROGRESS = new BigDecimal("100");

    @Resource
    private MilestoneMapper milestoneMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public Long create(MilestoneCreateRequest request) {
        Long userId = getCurrentUserId();
        if (request == null || request.getProjectId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不能为空");
        }
        validateProjectId(request.getProjectId());
        validateName(request.getName());
        ensureProjectOwnedByUser(request.getProjectId(), userId);

        int nextOrderNo = getNextOrderNo(request.getProjectId(), userId);
        Milestone milestone = new Milestone();
        milestone.setProjectId(request.getProjectId());
        milestone.setUserId(userId);
        milestone.setName(request.getName().trim());
        milestone.setOrderNo(nextOrderNo);
        milestone.setProgress(BigDecimal.ZERO);
        milestone.setIsDelete(0);

        int rows = milestoneMapper.insert(milestone);
        if (rows != 1 || milestone.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建里程碑失败");
        }
        return milestone.getId();
    }

    @Override
    public List<MilestoneVo> list(MilestoneQueryRequest request) {
        Long userId = getCurrentUserId();
        MilestoneQueryRequest validRequest = request == null ? new MilestoneQueryRequest() : request;
        if (validRequest.getProjectId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不能为空");
        }
        validateProjectId(validRequest.getProjectId());
        ensureProjectOwnedByUser(validRequest.getProjectId(), userId);

        LambdaQueryWrapper<Milestone> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Milestone::getUserId, userId)
                .eq(Milestone::getProjectId, validRequest.getProjectId());
        if (StringUtils.hasText(validRequest.getKeyword())) {
            wrapper.like(Milestone::getName, validRequest.getKeyword());
        }
        wrapper.orderByAsc(Milestone::getOrderNo);

        return milestoneMapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    @Override
    public void update(MilestoneUpdateRequest request) {
        Long userId = getCurrentUserId();
        if (request == null || request.getId() == null || request.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑 ID 不合法");
        }

        LambdaQueryWrapper<Milestone> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Milestone::getId, request.getId()).eq(Milestone::getUserId, userId);
        Milestone existing = milestoneMapper.selectOne(queryWrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "里程碑不存在");
        }

        boolean hasUpdateField = false;
        LambdaUpdateWrapper<Milestone> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Milestone::getId, request.getId()).eq(Milestone::getUserId, userId);

        if (request.getName() != null) {
            validateName(request.getName());
            updateWrapper.set(Milestone::getName, request.getName().trim());
            hasUpdateField = true;
        }

        if (request.getOrderNo() != null) {
            validateOrderNo(request.getOrderNo());
            ensureOrderNoUnique(existing.getProjectId(), userId, request.getOrderNo(), request.getId());
            updateWrapper.set(Milestone::getOrderNo, request.getOrderNo());
            hasUpdateField = true;
        }

        if (request.getProgress() != null) {
            validateProgress(request.getProgress());
            updateWrapper.set(Milestone::getProgress, request.getProgress());
            hasUpdateField = true;
        }

        if (!hasUpdateField) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "没有可更新的字段");
        }

        int rows = milestoneMapper.update(null, updateWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新里程碑失败");
        }
    }

    @Override
    public void delete(Long id) {
        Long userId = getCurrentUserId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑 ID 不合法");
        }

        LambdaQueryWrapper<Milestone> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Milestone::getId, id).eq(Milestone::getUserId, userId);
        Milestone existing = milestoneMapper.selectOne(queryWrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "里程碑不存在");
        }

        int rows = milestoneMapper.delete(queryWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除里程碑失败");
        }
    }

    private MilestoneVo toVo(Milestone milestone) {
        MilestoneVo vo = new MilestoneVo();
        BeanUtils.copyProperties(milestone, vo);
        return vo;
    }

    private int getNextOrderNo(Long projectId, Long userId) {
        LambdaQueryWrapper<Milestone> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Milestone::getProjectId, projectId)
                .eq(Milestone::getUserId, userId)
                .orderByDesc(Milestone::getOrderNo)
                .last("limit 1");
        Milestone latest = milestoneMapper.selectOne(wrapper);
        return latest == null || latest.getOrderNo() == null ? 1 : latest.getOrderNo() + 1;
    }

    private void ensureProjectOwnedByUser(Long projectId, Long userId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, projectId)
                .eq(Project::getUserId, userId)
                .isNull(Project::getDeletedAt);
        Project project = projectMapper.selectOne(wrapper);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private void ensureOrderNoUnique(Long projectId, Long userId, Integer orderNo, Long milestoneId) {
        LambdaQueryWrapper<Milestone> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Milestone::getProjectId, projectId)
                .eq(Milestone::getUserId, userId)
                .eq(Milestone::getOrderNo, orderNo)
                .ne(Milestone::getId, milestoneId);
        Milestone duplicate = milestoneMapper.selectOne(wrapper);
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该排序号已存在");
        }
    }

    private Long getCurrentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }

    private void validateProjectId(Long projectId) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑名称不能为空");
        }
        if (name.trim().length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑名称长度不能超过100");
        }
    }

    private void validateOrderNo(Integer orderNo) {
        if (orderNo == null || orderNo <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序号必须大于0");
        }
    }

    private void validateProgress(BigDecimal progress) {
        if (progress.compareTo(MIN_PROGRESS) < 0 || progress.compareTo(MAX_PROGRESS) > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "进度必须在0到100之间");
        }
        if (progress.scale() > 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "进度最多保留两位小数");
        }
    }
}


