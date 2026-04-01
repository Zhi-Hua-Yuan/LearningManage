package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.vo.dashboard.DashboardVO;
import com.spt.learningmanage.service.StatsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class StatsController {

    @Resource
    private StatsService statsService;

    @GetMapping("/overview")
    public BaseResponse<DashboardVO> getOverview() {
        return ResultUtils.ok(statsService.getOverview());
    }
}

