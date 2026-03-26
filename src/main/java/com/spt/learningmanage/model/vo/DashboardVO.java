package com.spt.learningmanage.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardVO {
    private CoreMetricsVO coreMetrics;
    private List<DailyTrendVO> dailyTrends;
    private List<ProjectRankingVO> projectRankings;
}

