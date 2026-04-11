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
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.service.WeeklyReviewService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class WeeklyReviewServiceImpl implements WeeklyReviewService {

    @Resource
    private WeeklyReviewMapper weeklyReviewMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

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
        return draft;
    }

    @Override
    public void saveReview(WeeklyReview weeklyReview) {
        Long userId = getCurrentUserId();
        validateSaveRequest(weeklyReview);

        WeeklyReview existing = findByUserYearWeek(userId, weeklyReview.getYear(), weeklyReview.getWeekNo());
        if (existing != null) {
            applyUpdatableFields(existing, weeklyReview);
            int rows = weeklyReviewMapper.updateById(existing);
            if (rows != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新周总结失败");
            }
            return;
        }

        WeeklyReview toSave = new WeeklyReview();
        toSave.setUserId(userId);
        toSave.setYear(weeklyReview.getYear());
        toSave.setWeekNo(weeklyReview.getWeekNo());
        toSave.setStartDate(weeklyReview.getStartDate());
        toSave.setEndDate(weeklyReview.getEndDate());
        toSave.setCompletedTaskCount(weeklyReview.getCompletedTaskCount() == null ? 0 : weeklyReview.getCompletedTaskCount());
        toSave.setFocusProjectName(weeklyReview.getFocusProjectName());
        toSave.setReflection(weeklyReview.getReflection());
        toSave.setNextPlan(weeklyReview.getNextPlan());

        int rows = weeklyReviewMapper.insert(toSave);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存周总结失败");
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
        if (!currentUserId.equals(review.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看他人的周总结");
        }

        return review;
    }

    @Override
    public void updateReview(WeeklyReview weeklyReview) {
        if (weeklyReview == null || weeklyReview.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "周总结ID不能为空");
        }

        WeeklyReview oldReview = weeklyReviewMapper.selectById(weeklyReview.getId());
        if (oldReview == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该周总结不存在");
        }
        if (!oldReview.getUserId().equals(getCurrentUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权修改他人的周总结");
        }

        oldReview.setReflection(weeklyReview.getReflection());
        oldReview.setNextPlan(weeklyReview.getNextPlan());
        int rows = weeklyReviewMapper.updateById(oldReview);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新周总结失败");
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

        if (!review.getUserId().equals(getCurrentUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除他人的周总结");
        }

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
        wrapper.eq(Task::getUserId, userId)
                .eq(Task::getStatus, TaskStatusEnum.DONE.getValue())
                .ge(Task::getCompletedAt, startDateTime)
                .lt(Task::getCompletedAt, endDateTimeExclusive);
        Long count = taskMapper.selectCount(wrapper);
        return count == null ? 0 : Math.toIntExact(count);
    }

    private String queryFocusProjectName(Long userId, LocalDateTime startDateTime, LocalDateTime endDateTimeExclusive) {
        QueryWrapper<Task> topProjectWrapper = new QueryWrapper<>();
        topProjectWrapper.select("project_id", "COUNT(*) AS completed_count")
                .eq("user_id", userId)
                .eq("status", TaskStatusEnum.DONE.getValue())
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
                .eq(Project::getUserId, userId)
                .last("limit 1");
        Project project = projectMapper.selectOne(projectWrapper);
        return project == null ? null : project.getName();
    }

    private void validateSaveRequest(WeeklyReview weeklyReview) {
        if (weeklyReview == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "周总结不能为空");
        }
        if (weeklyReview.getYear() == null || weeklyReview.getYear() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "year 不合法");
        }
        if (weeklyReview.getWeekNo() == null || weeklyReview.getWeekNo() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "weekNo 不合法");
        }
        if (weeklyReview.getCompletedTaskCount() != null && weeklyReview.getCompletedTaskCount() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "completedTaskCount 不能小于 0");
        }
        if (StringUtils.hasText(weeklyReview.getFocusProjectName()) && weeklyReview.getFocusProjectName().length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "focusProjectName 长度不能超过 100");
        }
    }

    private void applyUpdatableFields(WeeklyReview existing, WeeklyReview request) {
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

