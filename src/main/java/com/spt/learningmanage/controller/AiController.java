package com.spt.learningmanage.controller;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.vo.MilestoneDraftVO;
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

    @Operation(summary = "任务拆解", description = "根据目标、描述和周期生成里程碑与任务草稿")
    @PostMapping("/breakdown")
    public BaseResponse<List<MilestoneDraftVO>> breakdown(@RequestBody AiBreakdownRequest request) {
        if (request == null || StrUtil.hasBlank(request.getTarget(), request.getDescription(), request.getDuration())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "target、description、duration 不能为空");
        }
        List<MilestoneDraftVO> result = aiService.generateTaskBreakdown(
                request.getTarget(),
                request.getDescription(),
                request.getDuration()
        );
        return ResultUtils.ok(result);
    }

    @Operation(summary = "周总结润色", description = "根据任务完成数、核心项目和反思生成润色文本")
    @PostMapping("/polish")
    public BaseResponse<String> polish(@RequestBody AiPolishRequest request) {
        if (request == null || request.getTaskCount() == null
                || StrUtil.hasBlank(request.getFocusProject(), request.getReflection())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskCount、focusProject、reflection 不能为空");
        }
        String result = aiService.polishWeeklyReview(
                request.getTaskCount(),
                request.getFocusProject(),
                request.getReflection()
        );
        return ResultUtils.ok(result);
    }
}

