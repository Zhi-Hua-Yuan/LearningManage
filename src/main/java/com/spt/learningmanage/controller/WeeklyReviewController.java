package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.review.WeeklyReviewSaveRequest;
import com.spt.learningmanage.model.vo.review.WeeklyReviewSharedVO;
import com.spt.learningmanage.model.vo.review.WeeklyReviewVO;
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
    public BaseResponse<WeeklyReviewVO> getCurrentWeekReview() {
        return ResultUtils.success(weeklyReviewService.getCurrentWeekReviewView());
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
    public BaseResponse<WeeklyReviewVO> getReviewDetail(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数错误");
        }
        return ResultUtils.success(weeklyReviewService.getReviewViewById(id));
    }

    @Operation(summary = "更新周总结", description = "仅允许修改当前用户自己的周总结内容")
    @PostMapping("/update")
    public BaseResponse<Boolean> updateReview(@RequestBody WeeklyReviewSaveRequest request) {
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
    public BaseResponse<List<WeeklyReviewVO>> listHistory() {
        return ResultUtils.success(weeklyReviewService.listHistoryViews());
    }

    @Operation(summary = "获取团队共享周复盘摘要", description = "仅返回指定团队已发布的共享摘要，不包含私人正文")
    @GetMapping("/team")
    public BaseResponse<List<WeeklyReviewSharedVO>> listTeamReviews(
            @RequestParam Long teamId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer weekNo) {
        return ResultUtils.success(weeklyReviewService.listTeamSharedReviews(teamId, year, weekNo));
    }
}


