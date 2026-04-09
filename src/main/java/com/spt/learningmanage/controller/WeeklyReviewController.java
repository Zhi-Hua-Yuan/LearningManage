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
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "获取周总结详情", description = "根据ID获取特定周总结的详细信息")
    @GetMapping("/{id}")
    public BaseResponse<WeeklyReview> getReviewDetail(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数错误");
        }
        WeeklyReview review = weeklyReviewService.getReviewById(id);
        return ResultUtils.ok(review);
    }

    @Operation(summary = "更新周总结", description = "仅允许修改当前用户自己的周总结内容")
    @PostMapping("/update")
    public BaseResponse<Boolean> updateReview(@RequestBody WeeklyReview weeklyReview) {
        weeklyReviewService.updateReview(weeklyReview);
        return ResultUtils.ok(true);
    }

    @Operation(summary = "删除周总结", description = "仅允许删除当前用户自己的周总结")
    @PostMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteReview(@PathVariable("id") Long id) {
        weeklyReviewService.deleteReview(id);
        return ResultUtils.ok(true);
    }

    @Operation(summary = "获取历史周总结列表", description = "按 year 和 weekNo 倒序返回当前用户的历史周总结")
    @GetMapping("/history")
    public BaseResponse<List<WeeklyReview>> listHistory() {
        return ResultUtils.ok(weeklyReviewService.listHistory());
    }
}

