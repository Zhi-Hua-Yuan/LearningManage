package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.review.WeeklyReviewSaveRequest;
import com.spt.learningmanage.model.dto.review.WeeklyReviewTeamQueryRequest;
import com.spt.learningmanage.model.dto.review.WeeklyReviewUpdateRequest;
import com.spt.learningmanage.model.vo.review.WeeklyReviewDetailVO;
import com.spt.learningmanage.model.vo.review.WeeklyReviewSharedVO;
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
    public BaseResponse<WeeklyReviewDetailVO> getCurrentWeekReview() {
        return ResultUtils.success(weeklyReviewService.getCurrentWeekReview());
    }

    @Operation(summary = "保存或更新周总结", description = "接口描述")
    @PostMapping("/save")
    public BaseResponse<Boolean> saveReview(@RequestBody WeeklyReviewSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        weeklyReviewService.saveReview(request);
        return ResultUtils.success(true);
    }

    @Operation(summary = "获取周总结详情", description = "接口描述")
    @GetMapping("/{id}")
    public BaseResponse<WeeklyReviewDetailVO> getReviewDetail(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数错误");
        }
        WeeklyReviewDetailVO review = weeklyReviewService.getReviewById(id);
        return ResultUtils.success(review);
    }

    @Operation(summary = "更新周总结", description = "仅允许修改当前用户自己的周总结内容")
    @PostMapping("/update")
    public BaseResponse<Boolean> updateReview(@RequestBody WeeklyReviewUpdateRequest request) {
        weeklyReviewService.updateReview(request);
        return ResultUtils.success(true);
    }

    @Operation(summary = "删除周总结", description = "仅允许删除当前用户自己的周总结")
    @PostMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteReview(@PathVariable("id") Long id) {
        weeklyReviewService.deleteReview(id);
        return ResultUtils.success(true);
    }

    @Operation(summary = "获取历史周总结列表", description = "按 year 与 weekNo 倒序返回当前用户的历史周总结")
    @GetMapping("/history")
    public BaseResponse<List<WeeklyReviewDetailVO>> listHistory() {
        return ResultUtils.success(weeklyReviewService.listHistory());
    }

    @Operation(summary = "查询团队共享周复盘", description = "仅返回作者主动填写的共享摘要，不返回私人正文")
    @GetMapping("/team")
    public BaseResponse<Page<WeeklyReviewSharedVO>> listTeamSharedReviews(
            @RequestParam("teamId") Long teamId,
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "20") Long size) {
        WeeklyReviewTeamQueryRequest request = new WeeklyReviewTeamQueryRequest();
        request.setTeamId(teamId);
        request.setCurrent(current);
        request.setSize(size);
        return ResultUtils.success(weeklyReviewService.listTeamSharedReviews(request));
    }
}


