package com.spt.learningmanage.controller;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.AiListReplanCancelRequest;
import com.spt.learningmanage.model.dto.ai.AiListReplanConfirmRequest;
import com.spt.learningmanage.model.dto.ai.AiListReplanPreviewRequest;
import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
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

    @Operation(summary = "任务拆解")
    @PostMapping("/breakdown")
    public BaseResponse<List<MilestoneDraftVO>> breakdown(@RequestBody AiBreakdownRequest request) {
        if (request == null || StrUtil.hasBlank(request.getTarget(), request.getDuration())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "target、duration 不能为空");
        }
        boolean detailed = Boolean.TRUE.equals(request.getDetailed());
        List<MilestoneDraftVO> result = aiService.generateTaskBreakdown(
                request.getTarget(),
                request.getDescription(),
                request.getDuration(),
                detailed
        );
        return ResultUtils.ok(result);
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

    @Operation(summary = "清单任务智能重排预览", description = "生成重排结果供前端确认，不直接落库")
    @PostMapping("/list/replan/preview")
    public BaseResponse<AiListReplanPreviewVO> previewListReplan(@RequestBody AiListReplanPreviewRequest request) {
        if (request == null || request.getListId() == null || request.getListId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "listId 不合法");
        }
        return ResultUtils.ok(aiService.previewListReplan(request.getListId()));
    }

    @Operation(summary = "清单任务智能重排确认", description = "确认预览结果并入库")
    @PostMapping("/list/replan/confirm")
    public BaseResponse<Boolean> confirmListReplan(@RequestBody AiListReplanConfirmRequest request) {
        if (request == null || request.getListId() == null || request.getListId() <= 0 || StrUtil.isBlank(request.getOperationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        return ResultUtils.ok(aiService.confirmListReplan(request.getListId(), request.getOperationId()));
    }

    @Operation(summary = "清单任务智能重排取消", description = "取消预览，不入库")
    @PostMapping("/list/replan/cancel")
    public BaseResponse<Boolean> cancelListReplan(@RequestBody AiListReplanCancelRequest request) {
        if (request == null || StrUtil.isBlank(request.getOperationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "operationId 不能为空");
        }
        return ResultUtils.ok(aiService.cancelListReplan(request.getOperationId()));
    }

    @Operation(summary = "周总结润色")
    @PostMapping("/polish")
    public BaseResponse<String> polish(@RequestBody AiPolishRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        String result = aiService.polishWeeklyReview(request.getTaskIds(), request.getReflection());
        return ResultUtils.ok(result);
    }
}
