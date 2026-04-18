package com.spt.learningmanage.controller;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "任务拆解", description = "根据目标与周期（描述可选）生成里程碑与任务草稿")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "拆解成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(
                                    name = "任务拆解返回示例",
                                    value = "{\n"
                                            + "  \"code\": 0,\n"
                                            + "  \"message\": \"ok\",\n"
                                            + "  \"data\": [\n"
                                            + "    {\n"
                                            + "      \"name\": \"第一阶段：词汇与听力基础\",\n"
                                            + "      \"tasks\": [\n"
                                            + "        {\n"
                                            + "          \"name\": \"完成核心词汇第1-10单元\",\n"
                                            + "          \"priority\": 3,\n"
                                            + "          \"dueDate\": \"2026-04-25\"\n"
                                            + "        }\n"
                                            + "      ]\n"
                                            + "    }\n"
                                            + "  ]\n"
                                            + "}"
                            )
                    )
            )
    })
    @PostMapping("/breakdown")
    public BaseResponse<List<MilestoneDraftVO>> breakdown(@RequestBody AiBreakdownRequest request) {
        if (request == null || StrUtil.hasBlank(request.getTarget(), request.getDuration())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "target、duration 不能为空，description 可为空");
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

    @Operation(summary = "今日任务推荐顺序", description = "根据今天到期任务，结合难度、成本、效益等因素生成推荐完成顺序")
    @PostMapping("/today-order/recommend")
    public BaseResponse<AiTodayOrderVO> recommendTodayOrder(@RequestBody(required = false) AiTodayOrderRequest request) {
        return ResultUtils.ok(aiService.recommendTodayOrder(request));
    }

    @Operation(summary = "日报回顾改名建议", description = "根据当天任务完成情况，为未完成任务生成仅标题改名建议（不落库）")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "建议生成成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(
                                    name = "日报回顾改名建议返回示例",
                                    value = "{\n"
                                            + "  \"code\": 0,\n"
                                            + "  \"message\": \"ok\",\n"
                                            + "  \"data\": {\n"
                                            + "    \"operationId\": \"20260418_rename_9ab27d5f\",\n"
                                            + "    \"generatedAt\": \"2026-04-18T21:10:12\",\n"
                                            + "    \"reviewDate\": \"2026-04-18\",\n"
                                            + "    \"items\": [\n"
                                            + "      {\n"
                                            + "        \"taskId\": 101,\n"
                                            + "        \"oldTitle\": \"背单词\",\n"
                                            + "        \"newTitle\": \"完成核心词汇第11-12单元记忆\",\n"
                                            + "        \"reason\": \"标题更具体，可直接执行和验收\",\n"
                                            + "        \"confidence\": 86\n"
                                            + "      }\n"
                                            + "    ]\n"
                                            + "  }\n"
                                            + "}"
                            )
                    )
            )
    })
    @PostMapping("/daily-review/suggest-rename")
    public BaseResponse<DailyReviewSuggestRenameVO> suggestDailyReviewRename(
            @RequestBody(required = false) DailyReviewSuggestRenameRequest request) {
        return ResultUtils.ok(aiService.suggestDailyReviewRename(request));
    }

    @Operation(summary = "周总结润色", description = "根据任务列表和反思生成润色文本")
    @PostMapping("/polish")
    public BaseResponse<String> polish(@RequestBody AiPolishRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }

        String result = aiService.polishWeeklyReview(
                request.getTaskIds(),
                request.getReflection()
        );
        return ResultUtils.ok(result);
    }
}