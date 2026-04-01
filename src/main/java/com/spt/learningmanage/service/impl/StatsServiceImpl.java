package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.ProjectConstant;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.dashboard.CoreMetricsVO;
import com.spt.learningmanage.model.vo.dashboard.DailyTrendVO;
import com.spt.learningmanage.model.vo.dashboard.DashboardVO;
import com.spt.learningmanage.model.vo.dashboard.ProjectRankingVO;
import com.spt.learningmanage.service.StatsService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsServiceImpl implements StatsService {

    private static final int TOP_PROJECT_LIMIT = 5;
    private static final int TREND_DAYS = 7;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private TaskMapper taskMapper;

    @Override
    public DashboardVO getOverview() {
        Long userId = getCurrentUserId();
        LocalDate today = LocalDate.now();

        DashboardVO dashboardVO = new DashboardVO();
        dashboardVO.setCoreMetrics(buildCoreMetrics(userId, today));
        dashboardVO.setProjectRankings(buildProjectRankings(userId));
        dashboardVO.setDailyTrends(buildDailyTrends(userId, today));
        return dashboardVO;
    }

    private CoreMetricsVO buildCoreMetrics(Long userId, LocalDate today) {
        CoreMetricsVO coreMetricsVO = new CoreMetricsVO();

        LambdaQueryWrapper<Project> ongoingProjectWrapper = new LambdaQueryWrapper<>();
        ongoingProjectWrapper.eq(Project::getUserId, userId)
                .eq(Project::getStatus, ProjectConstant.STATUS_ACTIVE)
                .isNull(Project::getDeletedAt);
        coreMetricsVO.setOngoingProjectCount(toInteger(projectMapper.selectCount(ongoingProjectWrapper)));

        LambdaQueryWrapper<Task> overdueTaskWrapper = new LambdaQueryWrapper<>();
        overdueTaskWrapper.eq(Task::getUserId, userId)
                .lt(Task::getDueDate, today)
                .ne(Task::getStatus, TaskStatusEnum.DONE.getValue());
        coreMetricsVO.setOverdueTaskCount(toInteger(taskMapper.selectCount(overdueTaskWrapper)));

        LambdaQueryWrapper<Task> dueTodayTaskWrapper = new LambdaQueryWrapper<>();
        dueTodayTaskWrapper.eq(Task::getUserId, userId)
                .eq(Task::getDueDate, today)
                .ne(Task::getStatus, TaskStatusEnum.DONE.getValue());
        coreMetricsVO.setDueTodayTaskCount(toInteger(taskMapper.selectCount(dueTodayTaskWrapper)));

        return coreMetricsVO;
    }

    private List<ProjectRankingVO> buildProjectRankings(Long userId) {
        LambdaQueryWrapper<Project> rankingWrapper = new LambdaQueryWrapper<>();
        rankingWrapper.eq(Project::getUserId, userId)
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getProgress)
                .orderByDesc(Project::getUpdateTime)
                .last("limit " + TOP_PROJECT_LIMIT);

        return projectMapper.selectList(rankingWrapper).stream()
                .map(this::toProjectRankingVO)
                .toList();
    }

    private List<DailyTrendVO> buildDailyTrends(Long userId, LocalDate today) {
        LocalDate startDate = today.minusDays(TREND_DAYS - 1L);
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEndExclusive = today.plusDays(1L).atStartOfDay();

        LambdaQueryWrapper<Task> completedTaskWrapper = new LambdaQueryWrapper<>();
        completedTaskWrapper.eq(Task::getUserId, userId)
                .eq(Task::getStatus, TaskStatusEnum.DONE.getValue())
                .ge(Task::getCompletedAt, rangeStart)
                .lt(Task::getCompletedAt, rangeEndExclusive);
        List<Task> completedTaskList = taskMapper.selectList(completedTaskWrapper);

        Map<LocalDate, Integer> completedCountByDate = completedTaskList.stream()
                .filter(task -> task.getCompletedAt() != null)
                .collect(Collectors.groupingBy(task -> task.getCompletedAt().toLocalDate(), Collectors.summingInt(task -> 1)));

        List<DailyTrendVO> dailyTrendVOList = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate date = startDate.plusDays(i);
            DailyTrendVO dailyTrendVO = new DailyTrendVO();
            dailyTrendVO.setDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            dailyTrendVO.setCompletedCount(completedCountByDate.getOrDefault(date, 0));
            dailyTrendVOList.add(dailyTrendVO);
        }
        return dailyTrendVOList;
    }

    private ProjectRankingVO toProjectRankingVO(Project project) {
        ProjectRankingVO vo = new ProjectRankingVO();
        vo.setProjectName(project.getName());
        vo.setProgress(normalizeProgress(project.getProgress()));
        return vo;
    }

    private Integer normalizeProgress(BigDecimal progress) {
        if (progress == null) {
            return 0;
        }
        return progress.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private Integer toInteger(Long value) {
        if (value == null) {
            return 0;
        }
        return Math.toIntExact(value);
    }

    private Long getCurrentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}

