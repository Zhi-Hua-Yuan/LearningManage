package com.spt.learningmanage.controller;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
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
                                            + "        },\n"
                                            + "        {\n"
                                            + "          \"name\": \"每日听力训练30分钟\",\n"
                                            + "          \"priority\": 2,\n"
                                            + "          \"dueDate\": \"2026-04-28\"\n"
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
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "推荐成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(
                                    name = "今日任务推荐返回示例",
                                    value = "{\n"
                                            + "  \"code\": 0,\n"
                                            + "  \"message\": \"ok\",\n"
                                            + "  \"data\": {\n"
                                            + "    \"strategy\": \"balanced\",\n"
                                            + "    \"generatedAt\": \"2026-04-17T10:30:02\",\n"
                                            + "    \"fallbackUsed\": false,\n"
                                            + "    \"items\": [\n"
                                            + "      {\n"
                                            + "        \"taskId\": 102,\n"
                                            + "        \"title\": \"完成核心词汇第1-10单元\",\n"
                                            + "        \"rank\": 1,\n"
                                            + "        \"score\": 88,\n"
                                            + "        \"difficulty\": 3,\n"
                                            + "        \"cost\": 2,\n"
                                            + "        \"benefit\": 5,\n"
                                            + "        \"estimatedMinutes\": 30,\n"
                                            + "        \"reason\": \"收益高且可在30分钟内完成，建议优先处理\"\n"
                                            + "      }\n"
                                            + "    ]\n"
                                            + "  }\n"
                                            + "}"
                            )
                    )
            )
    })
    @PostMapping("/today-order/recommend")
    public BaseResponse<AiTodayOrderVO> recommendTodayOrder(@RequestBody(required = false) AiTodayOrderRequest request) {
        return ResultUtils.ok(aiService.recommendTodayOrder(request));
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
