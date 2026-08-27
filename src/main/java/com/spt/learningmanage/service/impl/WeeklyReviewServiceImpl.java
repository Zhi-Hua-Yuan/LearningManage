package com.spt.learningmanage.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.mapper.WeeklyReviewTaskMapper;
import com.spt.learningmanage.model.access.ProjectAccessScope;
import com.spt.learningmanage.model.dto.review.WeeklyReviewSaveRequest;
import com.spt.learningmanage.model.vo.review.WeeklyReviewSharedVO;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.entity.WeeklyReviewTask;
import com.spt.learningmanage.model.vo.review.WeeklyReviewVO;
import com.spt.learningmanage.service.WeeklyReviewService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WeeklyReviewServiceImpl implements WeeklyReviewService {

    @Resource
    private WeeklyReviewMapper weeklyReviewMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private WeeklyReviewTaskMapper weeklyReviewTaskMapper;

    @Resource
    private PermissionService permissionService;

    @Override
    public WeeklyReview getCurrentWeekReview() {
        Long userId = getCurrentUserId();

        DateTime now = DateUtil.date();
        int year = DateUtil.year(now);
        int weekNo = DateUtil.weekOfYear(now);

        LocalDate startDate = toLocalDate(DateUtil.beginOfWeek(now));
        LocalDate endDate = toLocalDate(DateUtil.endOfWeek(now));
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTimeExclusive = endDate.plusDays(1L).atStartOfDay();

        int completedTaskCount = countCompletedTasks(userId, startDateTime, endDateTimeExclusive);
        String focusProjectName = queryFocusProjectName(userId, startDateTime, endDateTimeExclusive);

        WeeklyReview existing = findByUserYearWeek(userId, year, weekNo);
        if (existing != null) {
            // Keep subjective content from saved review, but refresh computed snapshot fields.
            existing.setStartDate(startDate);
            existing.setEndDate(endDate);
            existing.setCompletedTaskCount(completedTaskCount);
            existing.setFocusProjectName(focusProjectName);
            if (!StringUtils.hasText(existing.getVisibilityScope())) {
                existing.setVisibilityScope("PRIVATE");
            }
            return existing;
        }

        WeeklyReview draft = new WeeklyReview();
        draft.setUserId(userId);
        draft.setYear(year);
        draft.setWeekNo(weekNo);
        draft.setStartDate(startDate);
        draft.setEndDate(endDate);
        draft.setCompletedTaskCount(completedTaskCount);
        draft.setFocusProjectName(focusProjectName);
        draft.setVisibilityScope("PRIVATE");
        return draft;
    }

    @Override
    public WeeklyReviewVO getCurrentWeekReviewView() {
        return toFullVO(getCurrentWeekReview());
    }

    @Override
    public void saveReview(WeeklyReview weeklyReview) {
        saveReview(toRequest(weeklyReview));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReview(WeeklyReviewSaveRequest request) {
        Long userId = getCurrentUserId();
        validateSaveRequest(request);

        WeeklyReview existing = request.getId() == null
                ? findByUserYearWeek(userId, request.getYear(), request.getWeekNo())
                : weeklyReviewMapper.selectById(request.getId());
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该周总结不存在");
        }

        WeeklyReview target = existing == null ? new WeeklyReview() : existing;
        if (existing == null) {
            target.setUserId(userId);
            target.setYear(request.getYear());
            target.setWeekNo(request.getWeekNo());
            target.setVisibilityScope(normalizeScope(request.getVisibilityScope()));
        }
        applyRequest(target, request);
        validateVisibilityAndAssociations(userId, target, request.getTaskIds());

        int rows = existing == null ? weeklyReviewMapper.insert(target) : weeklyReviewMapper.updateById(target);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, existing == null ? "保存周总结失败" : "更新周总结失败");
        }
        if (request.getTaskIds() != null) {
            replaceTaskLinks(target.getId(), request.getTaskIds());
        }
    }

    @Override
    public WeeklyReview getReviewById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的周总结ID");
        }

        WeeklyReview review = weeklyReviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该周总结不存在");
        }

        Long currentUserId = getCurrentUserId();
        permissionService.requireWeeklyReviewFullView(currentUserId, id);

        return review;
    }

    @Override
    public WeeklyReviewVO getReviewViewById(Long id) {
        return toFullVO(getReviewById(id));
    }

    @Override
    public void updateReview(WeeklyReview weeklyReview) {
        updateReview(toRequest(weeklyReview));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReview(WeeklyReviewSaveRequest request) {
        if (request == null || request.getId() == null || request.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "周总结ID不能为空");
        }
        Long userId = getCurrentUserId();
        WeeklyReview existing = weeklyReviewMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该周总结不存在");
        }
        permissionService.requireWeeklyReviewFullView(userId, request.getId());
        applyRequest(existing, request);
        validateVisibilityAndAssociations(userId, existing, request.getTaskIds());
        if (weeklyReviewMapper.updateById(existing) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新周总结失败");
        }
        if (request.getTaskIds() != null) {
            replaceTaskLinks(existing.getId(), request.getTaskIds());
        }
    }

    @Override
    public void deleteReview(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的周总结ID");
        }

        WeeklyReview review = weeklyReviewMapper.selectById(id);
        if (review == null) {
            return;
        }

        permissionService.requireWeeklyReviewFullView(getCurrentUserId(), id);

        int rows = weeklyReviewMapper.deleteById(id);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除周总结失败");
        }
    }

    @Override
    public List<WeeklyReview> listHistory() {
        Long userId = getCurrentUserId();
        LambdaQueryWrapper<WeeklyReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeeklyReview::getUserId, userId)
                .orderByDesc(WeeklyReview::getYear)
                .orderByDesc(WeeklyReview::getWeekNo);
        return weeklyReviewMapper.selectList(wrapper);
    }

    @Override
    public List<WeeklyReviewVO> listHistoryViews() {
        return listHistory().stream().map(this::toFullVO).toList();
    }

    @Override
    public List<WeeklyReviewSharedVO> listTeamSharedReviews(Long teamId, Integer year, Integer weekNo) {
        Long actorId = getCurrentUserId();
        permissionService.requireActiveTeamMember(actorId, teamId);
        LambdaQueryWrapper<WeeklyReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeeklyReview::getTeamId, teamId)
                .eq(WeeklyReview::getVisibilityScope, "TEAM")
                .orderByDesc(WeeklyReview::getYear)
                .orderByDesc(WeeklyReview::getWeekNo)
                .orderByDesc(WeeklyReview::getUpdateTime);
        if (year != null) {
            wrapper.eq(WeeklyReview::getYear, year);
        }
        if (weekNo != null) {
            wrapper.eq(WeeklyReview::getWeekNo, weekNo);
        }
        List<WeeklyReview> reviews = weeklyReviewMapper.selectList(wrapper);
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        return reviews.stream().map(this::toSharedVO).toList();
    }

    private WeeklyReview findByUserYearWeek(Long userId, Integer year, Integer weekNo) {
        LambdaQueryWrapper<WeeklyReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeeklyReview::getUserId, userId)
                .eq(WeeklyReview::getYear, year)
                .eq(WeeklyReview::getWeekNo, weekNo)
                .last("limit 1");
        return weeklyReviewMapper.selectOne(wrapper);
    }

    private int countCompletedTasks(Long userId, LocalDateTime startDateTime, LocalDateTime endDateTimeExclusive) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getAssigneeUserId, userId)
                .in(Task::getStatus,
                        TaskStatusEnum.DONE_BASIC.getValue(),
                        TaskStatusEnum.DONE_STANDARD.getValue(),
                        TaskStatusEnum.DONE_EXCELLENT.getValue())
                .ge(Task::getCompletedAt, startDateTime)
                .lt(Task::getCompletedAt, endDateTimeExclusive);
        Long count = taskMapper.selectCount(wrapper);
        return count == null ? 0 : Math.toIntExact(count);
    }

    private String queryFocusProjectName(Long userId, LocalDateTime startDateTime, LocalDateTime endDateTimeExclusive) {
        QueryWrapper<Task> topProjectWrapper = new QueryWrapper<>();
        topProjectWrapper.select("project_id", "COUNT(*) AS completed_count")
                .eq("assignee_user_id", userId)
                .in("status",
                        TaskStatusEnum.DONE_BASIC.getValue(),
                        TaskStatusEnum.DONE_STANDARD.getValue(),
                        TaskStatusEnum.DONE_EXCELLENT.getValue())
                .ge("completed_at", startDateTime)
                .lt("completed_at", endDateTimeExclusive)
                .groupBy("project_id")
                .orderByDesc("completed_count")
                .orderByAsc("project_id")
                .last("limit 1");

        List<Map<String, Object>> rows = taskMapper.selectMaps(topProjectWrapper);
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        Long projectId = castToLong(rows.get(0).get("project_id"));
        if (projectId == null) {
            return null;
        }

        LambdaQueryWrapper<Project> projectWrapper = new LambdaQueryWrapper<>();
        projectWrapper.eq(Project::getId, projectId)
                .last("limit 1");
        Project project = projectMapper.selectOne(projectWrapper);
        return project == null ? null : project.getName();
    }

    private void validateSaveRequest(WeeklyReviewSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "周总结不能为空");
        }
        if (request.getYear() == null || request.getYear() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "year 不合法");
        }
        if (request.getWeekNo() == null || request.getWeekNo() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "weekNo 不合法");
        }
        if (request.getCompletedTaskCount() != null && request.getCompletedTaskCount() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "completedTaskCount 不能小于 0");
        }
        if (StringUtils.hasText(request.getFocusProjectName()) && request.getFocusProjectName().length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "focusProjectName 长度不能超过 100");
        }
    }

    private void applyRequest(WeeklyReview existing, WeeklyReviewSaveRequest request) {
        if (request.getYear() != null) {
            existing.setYear(request.getYear());
        }
        if (request.getWeekNo() != null) {
            existing.setWeekNo(request.getWeekNo());
        }
        if (request.getStartDate() != null) {
            existing.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            existing.setEndDate(request.getEndDate());
        }
        if (request.getCompletedTaskCount() != null) {
            existing.setCompletedTaskCount(request.getCompletedTaskCount());
        }
        if (request.getFocusProjectName() != null) {
            existing.setFocusProjectName(request.getFocusProjectName());
        }
        if (request.getFocusProjectId() != null) {
            existing.setFocusProjectId(request.getFocusProjectId());
        }
        if (request.getVisibilityScope() != null) {
            existing.setVisibilityScope(normalizeScope(request.getVisibilityScope()));
        }
        if (request.getTeamId() != null || "PRIVATE".equals(existing.getVisibilityScope())) {
            existing.setTeamId(request.getTeamId());
        }
        if (request.getSharedSummary() != null) {
            existing.setSharedSummary(request.getSharedSummary());
        }
        if (request.getReflection() != null) {
            existing.setReflection(request.getReflection());
        }
        if (request.getNextPlan() != null) {
            existing.setNextPlan(request.getNextPlan());
        }

        if (existing.getCompletedTaskCount() == null) {
            existing.setCompletedTaskCount(0);
        }

        if (existing.getStartDate() != null && existing.getEndDate() != null
                && existing.getEndDate().isBefore(existing.getStartDate())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "endDate 不能早于 startDate");
        }
    }

    private WeeklyReviewSaveRequest toRequest(WeeklyReview review) {
        if (review == null) {
            return null;
        }
        WeeklyReviewSaveRequest request = new WeeklyReviewSaveRequest();
        request.setId(review.getId());
        request.setYear(review.getYear());
        request.setWeekNo(review.getWeekNo());
        request.setStartDate(review.getStartDate());
        request.setEndDate(review.getEndDate());
        request.setCompletedTaskCount(review.getCompletedTaskCount());
        request.setFocusProjectName(review.getFocusProjectName());
        request.setFocusProjectId(review.getFocusProjectId());
        request.setVisibilityScope(review.getVisibilityScope());
        request.setTeamId(review.getTeamId());
        request.setSharedSummary(review.getSharedSummary());
        request.setReflection(review.getReflection());
        request.setNextPlan(review.getNextPlan());
        return request;
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "PRIVATE";
        }
        String normalized = scope.trim().toUpperCase();
        if (!"PRIVATE".equals(normalized) && !"TEAM".equals(normalized)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "visibilityScope 只能是 PRIVATE 或 TEAM");
        }
        return normalized;
    }

    private void validateVisibilityAndAssociations(Long actorId, WeeklyReview review, List<Long> requestedTaskIds) {
        String scope = normalizeScope(review.getVisibilityScope());
        review.setVisibilityScope(scope);
        if ("PRIVATE".equals(scope)) {
            if (review.getTeamId() != null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "PRIVATE 周复盘不能设置 teamId");
            }
        } else {
            if (review.getTeamId() == null || review.getTeamId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "TEAM 周复盘必须指定 teamId");
            }
            if (!StringUtils.hasText(review.getSharedSummary())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "TEAM 周复盘共享摘要不能为空");
            }
            permissionService.requireActiveTeamMember(actorId, review.getTeamId());
        }

        if (review.getFocusProjectId() != null) {
            if (review.getFocusProjectId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "focusProjectId 不合法");
            }
            ProjectAccessScope scopeInfo = permissionService.requireProjectView(actorId, review.getFocusProjectId());
            if ("TEAM".equals(scope) && !review.getTeamId().equals(scopeInfo.teamId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "重点项目不属于指定团队");
            }
            if (review.getFocusProjectName() == null) {
                Project project = projectMapper.selectById(review.getFocusProjectId());
                if (project != null) {
                    review.setFocusProjectName(project.getName());
                }
            }
        }

        if (requestedTaskIds == null) {
            return;
        }
        Set<Long> taskIds = requestedTaskIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (taskIds.size() != requestedTaskIds.size()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 含有非法或重复 ID");
        }
        if (taskIds.isEmpty()) {
            return;
        }
        Set<Long> readable = permissionService.filterReadableTaskIds(actorId, taskIds);
        if (readable.size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "存在无权访问的关联任务");
        }
        if ("TEAM".equals(scope)) {
            List<Task> tasks = taskMapper.selectBatchIds(taskIds);
            Set<Long> projectIds = tasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
            List<Project> projects = projectIds.isEmpty() ? List.of() : projectMapper.selectBatchIds(projectIds);
            Map<Long, Project> projectMap = projects.stream().collect(Collectors.toMap(Project::getId, p -> p));
            if (tasks.size() != taskIds.size() || tasks.stream().anyMatch(task -> {
                Project project = projectMap.get(task.getProjectId());
                return project == null || !review.getTeamId().equals(project.getTeamId());
            })) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "TEAM 周复盘关联任务必须属于指定团队");
            }
        }
    }

    private void replaceTaskLinks(Long reviewId, List<Long> taskIds) {
        weeklyReviewTaskMapper.delete(new LambdaQueryWrapper<WeeklyReviewTask>()
                .eq(WeeklyReviewTask::getWeeklyReviewId, reviewId));
        LocalDateTime now = LocalDateTime.now();
        for (Long taskId : taskIds) {
            WeeklyReviewTask link = new WeeklyReviewTask();
            link.setWeeklyReviewId(reviewId);
            link.setTaskId(taskId);
            link.setCreateTime(now);
            if (weeklyReviewTaskMapper.insert(link) != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存周复盘任务关联失败");
            }
        }
    }

    private WeeklyReviewVO toFullVO(WeeklyReview review) {
        WeeklyReviewVO vo = new WeeklyReviewVO();
        org.springframework.beans.BeanUtils.copyProperties(review, vo);
        List<WeeklyReviewTask> links = weeklyReviewTaskMapper.selectList(new LambdaQueryWrapper<WeeklyReviewTask>()
                .eq(WeeklyReviewTask::getWeeklyReviewId, review.getId())
                .orderByAsc(WeeklyReviewTask::getId));
        vo.setTaskIds(links == null ? List.of() : links.stream().map(WeeklyReviewTask::getTaskId).toList());
        return vo;
    }

    private WeeklyReviewSharedVO toSharedVO(WeeklyReview review) {
        WeeklyReviewSharedVO vo = new WeeklyReviewSharedVO();
        vo.setId(review.getId());
        vo.setAuthorUserId(review.getUserId());
        vo.setYear(review.getYear());
        vo.setWeekNo(review.getWeekNo());
        vo.setStartDate(review.getStartDate());
        vo.setEndDate(review.getEndDate());
        vo.setFocusProjectId(review.getFocusProjectId());
        vo.setFocusProjectName(review.getFocusProjectName());
        vo.setSharedSummary(review.getSharedSummary());
        vo.setCreateTime(review.getCreateTime());
        vo.setUpdateTime(review.getUpdateTime());
        return vo;
    }

    private Long castToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && StringUtils.hasText(str)) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDate toLocalDate(java.util.Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Long getCurrentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}

