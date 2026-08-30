package com.spt.learningmanage.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.constant.WeeklyReviewVisibilityScopeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.review.WeeklyReviewSaveRequest;
import com.spt.learningmanage.model.dto.review.WeeklyReviewTeamQueryRequest;
import com.spt.learningmanage.model.dto.review.WeeklyReviewUpdateRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.vo.review.WeeklyReviewDetailVO;
import com.spt.learningmanage.model.vo.review.WeeklyReviewSharedVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.WeeklyReviewService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class WeeklyReviewServiceImpl implements WeeklyReviewService {

    private static final String PRIVATE_SCOPE = WeeklyReviewVisibilityScopeEnum.PRIVATE.getValue();
    private static final String TEAM_SCOPE = WeeklyReviewVisibilityScopeEnum.TEAM.getValue();

    @Resource
    private WeeklyReviewMapper weeklyReviewMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private PermissionService permissionService;

    @Override
    public WeeklyReviewDetailVO getCurrentWeekReview() {
        Long userId = getCurrentUserId();
        permissionService.requireActiveActor(userId);

        DateTime now = DateUtil.date();
        int year = DateUtil.year(now);
        int weekNo = DateUtil.weekOfYear(now);
        LocalDate startDate = toLocalDate(DateUtil.beginOfWeek(now));
        LocalDate endDate = toLocalDate(DateUtil.endOfWeek(now));
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTimeExclusive = endDate.plusDays(1L).atStartOfDay();

        WeeklyReview existing = findByUserYearWeek(userId, year, weekNo);
        if (existing != null) {
            existing.setStartDate(startDate);
            existing.setEndDate(endDate);
            existing.setCompletedTaskCount(countCompletedTasks(userId, startDateTime, endDateTimeExclusive));
            existing.setFocusProjectName(queryFocusProjectName(userId, startDateTime, endDateTimeExclusive));
            return toDetailVO(existing);
        }

        WeeklyReview draft = new WeeklyReview();
        draft.setUserId(userId);
        draft.setYear(year);
        draft.setWeekNo(weekNo);
        draft.setStartDate(startDate);
        draft.setEndDate(endDate);
        draft.setCompletedTaskCount(countCompletedTasks(userId, startDateTime, endDateTimeExclusive));
        draft.setFocusProjectName(queryFocusProjectName(userId, startDateTime, endDateTimeExclusive));
        draft.setVisibilityScope(PRIVATE_SCOPE);
        return toDetailVO(draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReview(WeeklyReviewSaveRequest request) {
        Long userId = getCurrentUserId();
        permissionService.requireActiveActor(userId);
        validateSaveRequest(request);

        WeeklyReviewVisibilityScopeEnum scope = normalizeScope(request.getVisibilityScope());
        validateVisibility(request.getTeamId(), request.getSharedSummary(), scope);
        rejectAssociationsUntilWp6c(request.getFocusProjectId(), request.getTaskIds());
        if (scope == WeeklyReviewVisibilityScopeEnum.TEAM) {
            permissionService.requireTeamView(userId, request.getTeamId());
        }

        WeeklyReview existing = findByUserYearWeek(userId, request.getYear(), request.getWeekNo());
        if (existing != null) {
            permissionService.requireWeeklyReviewUpdate(userId, existing.getId());
            applyRequest(existing, request, scope);
            updateReviewRow(existing);
            return;
        }

        WeeklyReview toSave = new WeeklyReview();
        toSave.setUserId(userId);
        toSave.setYear(request.getYear());
        toSave.setWeekNo(request.getWeekNo());
        LocalDate startDate = startOfIsoWeek(request.getYear(), request.getWeekNo());
        toSave.setStartDate(startDate);
        toSave.setEndDate(startDate.plusDays(6));
        toSave.setCompletedTaskCount(0);
        toSave.setVisibilityScope(scope.getValue());
        toSave.setTeamId(scope == WeeklyReviewVisibilityScopeEnum.TEAM ? request.getTeamId() : null);
        toSave.setFocusProjectId(null);
        toSave.setFocusProjectName(null);
        toSave.setSharedSummary(scope == WeeklyReviewVisibilityScopeEnum.TEAM
                ? request.getSharedSummary().trim() : null);
        toSave.setReflection(request.getReflection());
        toSave.setNextPlan(request.getNextPlan());

        if (weeklyReviewMapper.insert(toSave) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存周总结失败");
        }
    }

    @Override
    public WeeklyReviewDetailVO getReviewById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的周总结ID");
        }
        Long userId = getCurrentUserId();
        permissionService.requireWeeklyReviewFullView(userId, id);
        WeeklyReview review = weeklyReviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该周总结不存在");
        }
        return toDetailVO(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReview(WeeklyReviewUpdateRequest request) {
        if (request == null || request.getId() == null || request.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "周总结ID不能为空");
        }

        Long userId = getCurrentUserId();
        permissionService.requireWeeklyReviewUpdate(userId, request.getId());
        validateUpdateRequest(request);
        WeeklyReview existing = weeklyReviewMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该周总结不存在");
        }

        WeeklyReviewVisibilityScopeEnum scope = normalizeScope(request.getVisibilityScope());
        validateVisibility(request.getTeamId(), request.getSharedSummary(), scope);
        rejectAssociationsUntilWp6c(request.getFocusProjectId(), request.getTaskIds());
        if (scope == WeeklyReviewVisibilityScopeEnum.TEAM) {
            permissionService.requireTeamView(userId, request.getTeamId());
        }

        existing.setVisibilityScope(scope.getValue());
        existing.setTeamId(scope == WeeklyReviewVisibilityScopeEnum.TEAM ? request.getTeamId() : null);
        existing.setFocusProjectId(null);
        existing.setSharedSummary(scope == WeeklyReviewVisibilityScopeEnum.TEAM
                ? request.getSharedSummary().trim() : null);
        if (request.getReflection() != null) {
            existing.setReflection(request.getReflection());
        }
        if (request.getNextPlan() != null) {
            existing.setNextPlan(request.getNextPlan());
        }
        updateReviewRow(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的周总结ID");
        }
        Long userId = getCurrentUserId();
        WeeklyReview review = weeklyReviewMapper.selectById(id);
        if (review == null) {
            return;
        }
        permissionService.requireWeeklyReviewDelete(userId, id);
        if (weeklyReviewMapper.deleteById(id) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除周总结失败");
        }
    }

    @Override
    public List<WeeklyReviewDetailVO> listHistory() {
        Long userId = getCurrentUserId();
        permissionService.requireActiveActor(userId);
        List<WeeklyReview> reviews = weeklyReviewMapper.selectList(new LambdaQueryWrapper<WeeklyReview>()
                .eq(WeeklyReview::getUserId, userId)
                .orderByDesc(WeeklyReview::getYear)
                .orderByDesc(WeeklyReview::getWeekNo));
        return reviews.stream().map(this::toDetailVO).toList();
    }

    @Override
    public Page<WeeklyReviewSharedVO> listTeamSharedReviews(WeeklyReviewTeamQueryRequest request) {
        if (request == null || request.getTeamId() == null || request.getTeamId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "团队 ID 不合法");
        }
        Long userId = getCurrentUserId();
        permissionService.requireTeamView(userId, request.getTeamId());
        long current = safePage(request.getCurrent(), 1L);
        long size = Math.min(safePage(request.getSize(), 20L), 100L);
        return weeklyReviewMapper.selectTeamSharedPage(
                new Page<>(current, size), request.getTeamId());
    }

    private void updateReviewRow(WeeklyReview review) {
        if (review.getVisibilityScope() == null) {
            review.setVisibilityScope(PRIVATE_SCOPE);
        }
        if (weeklyReviewMapper.updateById(review) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新周总结失败");
        }
    }

    private void applyRequest(WeeklyReview existing, WeeklyReviewSaveRequest request,
                              WeeklyReviewVisibilityScopeEnum scope) {
        existing.setVisibilityScope(scope.getValue());
        existing.setTeamId(scope == WeeklyReviewVisibilityScopeEnum.TEAM ? request.getTeamId() : null);
        existing.setFocusProjectId(null);
        existing.setSharedSummary(scope == WeeklyReviewVisibilityScopeEnum.TEAM
                ? request.getSharedSummary().trim() : null);
        if (request.getReflection() != null) {
            existing.setReflection(request.getReflection());
        }
        if (request.getNextPlan() != null) {
            existing.setNextPlan(request.getNextPlan());
        }
    }

    private WeeklyReviewDetailVO toDetailVO(WeeklyReview review) {
        WeeklyReviewDetailVO vo = new WeeklyReviewDetailVO();
        vo.setId(review.getId());
        vo.setAuthorUserId(review.getUserId());
        vo.setYear(review.getYear());
        vo.setWeekNo(review.getWeekNo());
        vo.setStartDate(review.getStartDate());
        vo.setEndDate(review.getEndDate());
        vo.setCompletedTaskCount(review.getCompletedTaskCount() == null ? 0 : review.getCompletedTaskCount());
        vo.setVisibilityScope(normalizeScopeValue(review.getVisibilityScope()));
        vo.setTeamId(review.getTeamId());
        vo.setFocusProjectId(review.getFocusProjectId());
        vo.setFocusProjectName(review.getFocusProjectName());
        vo.setSharedSummary(review.getSharedSummary());
        vo.setReflection(review.getReflection());
        vo.setNextPlan(review.getNextPlan());
        vo.setTaskIds(Collections.emptyList());
        vo.setCreateTime(review.getCreateTime());
        vo.setUpdateTime(review.getUpdateTime());
        return vo;
    }

    private WeeklyReviewVisibilityScopeEnum normalizeScope(String value) {
        if (value == null) {
            return WeeklyReviewVisibilityScopeEnum.PRIVATE;
        }
        WeeklyReviewVisibilityScopeEnum scope = WeeklyReviewVisibilityScopeEnum.fromValue(value);
        if (scope == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "visibilityScope 不合法");
        }
        return scope;
    }

    private String normalizeScopeValue(String value) {
        return normalizeScope(value).getValue();
    }

    private void validateVisibility(Long teamId, String sharedSummary,
                                    WeeklyReviewVisibilityScopeEnum scope) {
        if (scope == WeeklyReviewVisibilityScopeEnum.PRIVATE) {
            if (teamId != null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "PRIVATE 周总结不能指定团队");
            }
            return;
        }
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "TEAM 周总结必须指定团队");
        }
        if (!StringUtils.hasText(sharedSummary)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "TEAM 周总结共享摘要不能为空");
        }
    }

    private void rejectAssociationsUntilWp6c(Long focusProjectId, List<Long> taskIds) {
        if (focusProjectId != null || (taskIds != null && !taskIds.isEmpty())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "周总结资源关联将在后续工作包启用");
        }
    }

    private void validateSaveRequest(WeeklyReviewSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "周总结不能为空");
        }
        if (request.getYear() == null || request.getYear() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "year 不合法");
        }
        if (request.getWeekNo() == null || request.getWeekNo() <= 0 || request.getWeekNo() > 53) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "weekNo 不合法");
        }
    }

    private void validateUpdateRequest(WeeklyReviewUpdateRequest request) {
        if (request.getTaskIds() != null && request.getTaskIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 不合法");
        }
    }

    private WeeklyReview findByUserYearWeek(Long userId, Integer year, Integer weekNo) {
        return weeklyReviewMapper.selectOne(new LambdaQueryWrapper<WeeklyReview>()
                .eq(WeeklyReview::getUserId, userId)
                .eq(WeeklyReview::getYear, year)
                .eq(WeeklyReview::getWeekNo, weekNo)
                .last("limit 1"));
    }

    private int countCompletedTasks(Long userId, LocalDateTime startDateTime,
                                    LocalDateTime endDateTimeExclusive) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getCreatedByUserId, userId)
                .in(Task::getStatus,
                        TaskStatusEnum.DONE_BASIC.getValue(),
                        TaskStatusEnum.DONE_STANDARD.getValue(),
                        TaskStatusEnum.DONE_EXCELLENT.getValue())
                .ge(Task::getCompletedAt, startDateTime)
                .lt(Task::getCompletedAt, endDateTimeExclusive));
        return count == null ? 0 : Math.toIntExact(count);
    }

    private String queryFocusProjectName(Long userId, LocalDateTime startDateTime,
                                         LocalDateTime endDateTimeExclusive) {
        QueryWrapper<Task> wrapper = new QueryWrapper<>();
        wrapper.select("project_id", "COUNT(*) AS completed_count")
                .eq("user_id", userId)
                .in("status", TaskStatusEnum.DONE_BASIC.getValue(),
                        TaskStatusEnum.DONE_STANDARD.getValue(),
                        TaskStatusEnum.DONE_EXCELLENT.getValue())
                .ge("completed_at", startDateTime)
                .lt("completed_at", endDateTimeExclusive)
                .groupBy("project_id")
                .orderByDesc("completed_count")
                .orderByAsc("project_id")
                .last("limit 1");
        List<Map<String, Object>> rows = taskMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Object value = rows.get(0).get("project_id");
        Long projectId = value instanceof Number number ? number.longValue() : null;
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, projectId)
                .eq(Project::getUserId, userId)
                .last("limit 1"));
        return project == null ? null : project.getName();
    }

    private LocalDate startOfIsoWeek(int year, int weekNo) {
        try {
            return LocalDate.of(year, 1, 4)
                    .with(WeekFields.ISO.weekBasedYear(), year)
                    .with(WeekFields.ISO.weekOfWeekBasedYear(), weekNo)
                    .with(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "weekNo 不合法");
        }
    }

    private LocalDate toLocalDate(java.util.Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private long safePage(Long value, long defaultValue) {
        return value == null || value < 1 ? defaultValue : value;
    }

    private Long getCurrentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}
