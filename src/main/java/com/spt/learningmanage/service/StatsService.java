package com.spt.learningmanage.service;

import com.spt.learningmanage.model.vo.DashboardVO;

public interface StatsService {
    /**
     * 获取当前登录用户的 Dashboard 概览数据。
     */
    DashboardVO getOverview();
}

