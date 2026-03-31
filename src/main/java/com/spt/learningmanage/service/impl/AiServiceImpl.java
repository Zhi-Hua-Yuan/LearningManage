package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.vo.MilestoneDraftVO;
import com.spt.learningmanage.service.AiService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiServiceImpl implements AiService {

    private static final String TASK_BREAKDOWN_SYSTEM_PROMPT = "你是一个资深项目经理。请根据用户的目标、描述和周期，将项目拆解为2-4个里程碑，每个里程碑包含2-5个具体任务。你必须严格返回纯 JSON 数组格式，绝对不要包含任何 markdown 标记（如 ```json）或其他说明文字。JSON 结构必须是：[{\"name\":\"里程碑1\", \"tasks\":[{\"name\":\"任务1\"}]}]";
    private static final String WEEKLY_POLISH_SYSTEM_PROMPT = "你是一个懂心理学和目标管理的导师。请根据用户本周完成的任务数、核心项目以及反思，写一段 100-200 字的结构化总结，并给出 3 条具体的下周行动建议。";

    @Resource
    private AiProperties aiProperties;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (StrUtil.isBlank(systemPrompt) || StrUtil.isBlank(userPrompt)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词不能为空");
        }
        return callAi(systemPrompt, userPrompt);
    }

    @Override
    public List<MilestoneDraftVO> generateTaskBreakdown(String target, String description, String duration) {
        if (StrUtil.hasBlank(target, description, duration)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标、描述和周期不能为空");
        }

        String userPrompt = String.format("目标：%s，描述：%s，周期：%s。", target, description, duration);
        String aiRawContent = callAi(TASK_BREAKDOWN_SYSTEM_PROMPT, userPrompt);
        String jsonText = sanitizeJsonText(aiRawContent);

        try {
            JSONArray jsonArray = JSONUtil.parseArray(jsonText);
            return JSONUtil.toList(jsonArray, MilestoneDraftVO.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务拆解结果解析失败: " + e.getMessage());
        }
    }

    @Override
    public String polishWeeklyReview(Integer taskCount, String focusProject, String reflection) {
        if (taskCount == null || StrUtil.hasBlank(focusProject, reflection)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务数、核心项目和反思不能为空");
        }
        String userPrompt = String.format("本周完成任务数：%d，主要项目：%s，用户主观反思：%s。", taskCount, focusProject, reflection);
        return callAi(WEEKLY_POLISH_SYSTEM_PROMPT, userPrompt);
    }

    private String sanitizeJsonText(String content) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回内容为空");
        }

        String cleaned = content.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int startIndex = cleaned.indexOf('[');
        int endIndex = cleaned.lastIndexOf(']');
        if (startIndex >= 0 && endIndex > startIndex) {
            cleaned = cleaned.substring(startIndex, endIndex + 1);
        }
        return cleaned;
    }

    private String callAi(String systemPrompt, String userPrompt) {
        String baseUrl = aiProperties.getBaseUrl();
        String apiKey = aiProperties.getApiKey();
        String model = aiProperties.getModel();

        if (StrUtil.hasBlank(baseUrl, apiKey, model)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 配置不完整，请检查 ai.base-url、ai.api-key、ai.model");
        }

        JSONObject requestBody = JSONUtil.createObj()
                .set("model", model)
                .set("messages", JSONUtil.createArray()
                        .put(JSONUtil.createObj().set("role", "system").set("content", systemPrompt))
                        .put(JSONUtil.createObj().set("role", "user").set("content", userPrompt)));

        int statusCode;
        String responseBody;
        try {
            try (HttpResponse response = HttpRequest.post(StrUtil.removeSuffix(baseUrl, "/") + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", ContentType.JSON.getValue())
                    .body(requestBody.toString())
                    .execute()) {
                statusCode = response.getStatus();
                responseBody = response.body();
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 请求失败: " + e.getMessage());
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 接口调用失败: " + responseBody);
        }

        try {
            JSONObject responseJson = JSONUtil.parseObj(responseBody);
            JSONArray choices = responseJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回结果缺少 choices");
            }

            JSONObject firstChoice = choices.getJSONObject(0);
            if (firstChoice == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回结果格式错误: choice 为空");
            }

            JSONObject message = firstChoice.getJSONObject("message");
            if (message == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回结果格式错误: message 为空");
            }

            String content = message.getStr("content");
            if (StrUtil.isBlank(content)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回内容为空");
            }
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解析 AI 返回结果失败: " + e.getMessage());
        }
    }
}


