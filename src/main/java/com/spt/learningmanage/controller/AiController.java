package com.spt.learningmanage.controller;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiBreakdownConfirmRequest;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.AiDraftCancelRequest;
import com.spt.learningmanage.model.dto.ai.AiListReplanCancelRequest;
import com.spt.learningmanage.model.dto.ai.AiListReplanConfirmRequest;
import com.spt.learningmanage.model.dto.ai.AiListReplanPreviewRequest;
import com.spt.learningmanage.model.dto.ai.AiPolishConfirmRequest;
import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.vo.ai.AiBreakdownPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.ai.AiDraftDetailVO;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiPolishPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI", description = "AI 辅助功能")
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiService aiService;

    @Operation(summary = "任务拆解草稿（兼容旧版）")
    @PostMapping("/breakdown")
    public BaseResponse<List<MilestoneDraftVO>> breakdown(@RequestBody AiBreakdownRequest request) {
        if (request == null || StrUtil.hasBlank(request.getTarget(), request.getDuration())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标和周期不能为空");
        }
        boolean detailed = Boolean.TRUE.equals(request.getDetailed());
        return ResultUtils.ok(aiService.generateTaskBreakdown(
                request.getTarget(),
                request.getDescription(),
                request.getDuration(),
                detailed
        ));
    }

    @Operation(summary = "任务拆解预览")
    @PostMapping("/breakdown/preview")
    public BaseResponse<AiBreakdownPreviewVO> previewBreakdown(@RequestBody AiBreakdownRequest request) {
        if (request == null || StrUtil.hasBlank(request.getTarget(), request.getDuration())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标和周期不能为空");
        }
        return ResultUtils.ok(aiService.previewTaskBreakdown(request));
    }

    @Operation(summary = "任务拆解确认")
    @PostMapping("/breakdown/confirm")
    public BaseResponse<AiDraftConfirmVO> confirmBreakdown(@RequestBody AiBreakdownConfirmRequest request) {
        if (request == null || StrUtil.hasBlank(request.getDraftId(), request.getOperationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿ID和操作ID不能为空");
        }
        return ResultUtils.ok(aiService.confirmTaskBreakdown(
                request.getDraftId(),
                request.getOperationId(),
                request.getProjectName(),
                request.getProjectGoal()
        ));
    }

    @Operation(summary = "今日任务推荐顺序")
    @PostMapping("/today-order/recommend")
    public BaseResponse<AiTodayOrderVO> recommendTodayOrder(@RequestBody(required = false) AiTodayOrderRequest request) {
        return ResultUtils.ok(aiService.recommendTodayOrder(request));
    }

    @Operation(summary = "日报回顾改名建议")
    @PostMapping("/daily-review/suggest-rename")
    public BaseResponse<DailyReviewSuggestRenameVO> suggestDailyReviewRename(
            @RequestBody(required = false) DailyReviewSuggestRenameRequest request) {
        return ResultUtils.ok(aiService.suggestDailyReviewRename(request));
    }

    @Operation(summary = "清单任务智能重排预览")
    @PostMapping("/list/replan/preview")
    public BaseResponse<AiListReplanPreviewVO> previewListReplan(@RequestBody AiListReplanPreviewRequest request) {
        if (request == null || request.getListId() == null || request.getListId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "清单ID不合法");
        }
        return ResultUtils.ok(aiService.previewListReplan(request.getListId()));
    }

    @Operation(summary = "清单任务智能重排确认")
    @PostMapping("/list/replan/confirm")
    public BaseResponse<Boolean> confirmListReplan(@RequestBody AiListReplanConfirmRequest request) {
        if (request == null || request.getListId() == null || request.getListId() <= 0 || StrUtil.isBlank(request.getOperationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        return ResultUtils.ok(aiService.confirmListReplan(request.getListId(), request.getOperationId()));
    }

    @Operation(summary = "清单任务智能重排取消")
    @PostMapping("/list/replan/cancel")
    public BaseResponse<Boolean> cancelListReplan(@RequestBody AiListReplanCancelRequest request) {
        if (request == null || StrUtil.isBlank(request.getOperationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "操作ID不能为空");
        }
        return ResultUtils.ok(aiService.cancelListReplan(request.getOperationId()));
    }

    @Operation(summary = "周总结润色草稿（兼容旧版）")
    @PostMapping("/polish")
    public BaseResponse<String> polish(@RequestBody AiPolishRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        return ResultUtils.ok(aiService.polishWeeklyReview(request.getTaskIds(), request.getReflection()));
    }

    @Operation(summary = "周总结润色预览")
    @PostMapping("/polish/preview")
    public BaseResponse<AiPolishPreviewVO> previewPolish(@RequestBody AiPolishRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        return ResultUtils.ok(aiService.previewWeeklyPolish(request));
    }

    @Operation(summary = "周总结润色确认")
    @PostMapping("/polish/confirm")
    public BaseResponse<AiDraftConfirmVO> confirmPolish(@RequestBody AiPolishConfirmRequest request) {
        if (request == null || StrUtil.hasBlank(request.getDraftId(), request.getOperationId())
                || request.getReviewId() == null || request.getReviewId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿ID、操作ID和周总结ID不能为空");
        }
        return ResultUtils.ok(aiService.confirmWeeklyPolish(request.getDraftId(), request.getOperationId(), request.getReviewId()));
    }

    @Operation(summary = "取消 AI 草稿")
    @PostMapping("/draft/cancel")
    public BaseResponse<Boolean> cancelDraft(@RequestBody AiDraftCancelRequest request) {
        if (request == null || StrUtil.isBlank(request.getDraftId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿ID不能为空");
        }
        return ResultUtils.ok(aiService.cancelDraft(request.getDraftId(), null));
    }

    @Operation(summary = "获取 AI 草稿详情")
    @GetMapping("/draft/{draftId}")
    public BaseResponse<AiDraftDetailVO> getDraft(@PathVariable String draftId) {
        if (StrUtil.isBlank(draftId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿ID不能为空");
        }
        return ResultUtils.ok(aiService.getDraftDetail(draftId));
    }
}
