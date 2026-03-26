package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.service.WeeklyReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "WeeklyReview", description = "周总结模块")
@RestController
@RequestMapping("/review")
public class WeeklyReviewController {

    @Resource
    private WeeklyReviewService weeklyReviewService;

    @Operation(summary = "获取当前周总结草稿", description = "若数据库已有当前周记录则直接返回，否则动态计算草稿并返回")
    @GetMapping("/current")
    public BaseResponse<WeeklyReview> getCurrentWeekReview() {
        return ResultUtils.ok(weeklyReviewService.getCurrentWeekReview());
    }

    @Operation(summary = "保存或更新周总结", description = "根据 year + weekNo 判断，存在则更新，不存在则新增")
    @PostMapping("/save")
    public BaseResponse<Boolean> saveReview(@RequestBody WeeklyReview weeklyReview) {
        if (weeklyReview == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        weeklyReviewService.saveReview(weeklyReview);
        return ResultUtils.ok(true);
    }

    @Operation(summary = "获取历史周总结列表", description = "按 year 和 weekNo 倒序返回当前用户的历史周总结")
    @GetMapping("/history")
    public BaseResponse<List<WeeklyReview>> listHistory() {
        return ResultUtils.ok(weeklyReviewService.listHistory());
    }
}

