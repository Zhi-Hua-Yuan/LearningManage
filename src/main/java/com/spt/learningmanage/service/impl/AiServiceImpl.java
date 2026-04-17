package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.model.vo.milestone.TaskDraftVO;
import com.spt.learningmanage.service.AiService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private static final int MAX_POLISH_TASK_COUNT = 50;
    private static final String EMPTY_REFLECTION_PLACEHOLDER = "（用户未填写反思）";

    private static final int PROJECT_NAME_MAX_LEN = 100;
    private static final int TASK_TITLE_MAX_LEN = 60;

    private static final String TASK_BREAKDOWN_SYSTEM_PROMPT_DEFAULT = "你是一名资深项目经理与学习规划顾问。"
            + "请根据用户目标、周期和补充描述，输出可执行的项目拆解。"
            + "要求："
            + "1) 仅输出纯 JSON 数组，不允许 Markdown 或解释文本；"
            + "2) 里程碑 2-4 个，按推进顺序；"
            + "3) 每个里程碑 2-5 个任务；"
            + "4) 任务名称要具体可执行，避免空泛表述；"
            + "5) 里程碑与任务避免重复；"
            + "6) 每个里程碑名称(name)长度不得超过100个字符；"
            + "7) 每个任务名称(name)长度不得超过60个字符。"
            + "严格输出结构："
            + "[{\"name\":\"里程碑1\",\"tasks\":[{\"name\":\"任务1\"}]}]";

    private static final String TASK_BREAKDOWN_SYSTEM_PROMPT_DETAILED = "你是一名资深项目经理与学习规划顾问。"
            + "现在需要你输出更细颗粒度、更可落地的执行计划。"
            + "要求："
            + "1) 仅输出纯 JSON 数组，不允许 Markdown 或任何说明文字；"
            + "2) 里程碑 3-4 个，必须体现阶段递进关系（准备->执行->巩固/验收）；"
            + "3) 每个里程碑 4-6 个任务；"
            + "4) 每个任务必须具体、可操作、可检查，尽量动词开头；"
            + "5) 优先输出有产出物的任务（如提交、完成、复盘、测试、演练）；"
            + "6) 任务尽量避免重复，名称长度建议 8-24 字；"
            + "7) 如果用户描述信息不足，也要基于目标与周期给出合理拆解；"
            + "8) 每个里程碑名称(name)长度不得超过100个字符；"
            + "9) 每个任务名称(name)长度不得超过60个字符。"
            + "严格输出结构："
            + "[{\"name\":\"里程碑1\",\"tasks\":[{\"name\":\"任务1\"}]}]";

    private static final String WEEKLY_POLISH_SYSTEM_PROMPT = "你是一个专业的职场与学业规划 AI 助手，擅长周复盘总结。"
            + "请基于用户的任务上下文与主观反思，生成高质量本周复盘。"
            + "硬性要求："
            + "1) 只输出合法 JSON 字符串；"
            + "2) 绝对不要输出 Markdown、代码块标记（如 ```json）或解释文字；"
            + "3) 输出结构必须严格为：{\"review\":\"...\"}。"
            + "内容要求："
            + "A) review：100-220字，结构化描述（完成情况、关键进展、问题与原因）；"
            + "B) 语气积极、具体，不空泛，不编造不存在的数据；"
            + "C) 若用户未填写反思，也需基于任务上下文给出客观复盘。";

    @Resource
    private AiProperties aiProperties;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (StrUtil.isBlank(systemPrompt) || StrUtil.isBlank(userPrompt)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词不能为空");
        }
        return callAiWithFallback(aiProperties.getModel(), systemPrompt, userPrompt);
    }

    @Override
    public List<MilestoneDraftVO> generateTaskBreakdown(String target, String description, String duration, boolean detailed) {
        if (StrUtil.hasBlank(target, duration)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标和周期不能为空，描述可为空");
        }

        String normalizedTarget = target.trim();
        if (normalizedTarget.length() > PROJECT_NAME_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标长度不能超过100个字符");
        }

        String userPrompt = String.format("目标：%s，周期：%s。", normalizedTarget, duration.trim());
        if (StrUtil.isNotBlank(description)) {
            userPrompt = userPrompt + String.format("描述：%s。", description.trim());
        }

        String systemPrompt = detailed ? TASK_BREAKDOWN_SYSTEM_PROMPT_DETAILED : TASK_BREAKDOWN_SYSTEM_PROMPT_DEFAULT;
        String aiRawContent = callAiWithFallback(aiProperties.getBreakdownModel(), systemPrompt, userPrompt);
        String jsonText = sanitizeJsonArrayText(aiRawContent);

        try {
            JSONArray jsonArray = JSONUtil.parseArray(jsonText);
            List<MilestoneDraftVO> result = JSONUtil.toList(jsonArray, MilestoneDraftVO.class);
            // 轻量模式：生成阶段不做硬拦截，导入阶段再做最终校验并返回失败项。
            // normalizeAndValidateDrafts(result);
            logDraftLengthRisk(result, normalizedTarget, detailed);
            if (result == null || result.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.OPERATION_ERROR,
                        "AI 未生成可用草稿，请调整描述后重试（避免与名称长度约束冲突）"
                );
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务拆解结果解析失败，请重试。原始异常: " + e.getMessage());
        }
    }

    @Override
    public String polishWeeklyReview(List<Long> taskIds, String reflection) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录状态已失效，请重新登录");
        }

        List<Long> validTaskIds = taskIds == null
                ? new ArrayList<>()
                : taskIds.stream().filter(id -> id != null && id > 0).collect(Collectors.toCollection(ArrayList::new));

        if (taskIds != null && !taskIds.isEmpty() && validTaskIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 至少需要包含一个有效的正整数ID");
        }

        if (validTaskIds.isEmpty()) {
            return JSONUtil.createObj()
                    .set("review", "本周暂无已完成任务记录。你可以先从最小可执行任务开始，逐步恢复节奏。")
                    .toString();
        }

        Set<Long> uniqueTaskIds = new LinkedHashSet<>(validTaskIds);
        List<Task> taskList = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getId, uniqueTaskIds)
                .eq(Task::getUserId, currentUserId)
                .orderByDesc(Task::getCompletedAt, Task::getUpdateTime, Task::getId));

        if (taskList.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "传入的任务均不存在或无访问权限，请确认任务ID是否属于当前登录账号");
        }

        Set<Long> foundIds = taskList.stream().map(Task::getId).collect(Collectors.toSet());
        List<Long> missingIds = uniqueTaskIds.stream().filter(id -> !foundIds.contains(id)).toList();

        int actualTaskCount = taskList.size();
        List<Task> limitedTaskList = taskList.stream().limit(MAX_POLISH_TASK_COUNT).toList();

        Set<Long> projectIds = limitedTaskList.stream()
                .map(Task::getProjectId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, Project> projectMap = projectIds.isEmpty()
                ? Map.of()
                : projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .in(Project::getId, projectIds)
                        .eq(Project::getUserId, currentUserId))
                .stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (a, b) -> a));

        JSONArray taskContext = JSONUtil.createArray();
        for (Task task : limitedTaskList) {
            Project project = projectMap.get(task.getProjectId());
            taskContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId())
                    .set("taskTitle", task.getTitle())
                    .set("taskDescription", task.getDescription())
                    .set("status", task.getStatus())
                    .set("dueDate", task.getDueDate())
                    .set("completedAt", task.getCompletedAt())
                    .set("projectId", task.getProjectId())
                    .set("projectName", project == null ? "未识别项目" : project.getName()));
        }

        String reflectionText = StrUtil.blankToDefault(reflection, EMPTY_REFLECTION_PLACEHOLDER);

        String userPrompt = "本周完成任务数（后端计算）：" + actualTaskCount
                + "\n本周任务明细（JSON）：" + taskContext
                + "\n任务ID缺失或无权限数量：" + missingIds.size()
                + "\n缺失任务ID（仅供参考）：" + missingIds
                + "\n用户主观反思：" + reflectionText;

        String aiRawContent = callAiWithFallback(aiProperties.getPolishModel(), WEEKLY_POLISH_SYSTEM_PROMPT, userPrompt);
        String cleanedResult = sanitizeJsonObjectText(aiRawContent);

        try {
            JSONObject resultObj = JSONUtil.parseObj(cleanedResult);
            String review = resultObj.getStr("review");
            if (StrUtil.isBlank(review)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "周总结润色结果缺少 review 字段，请重试");
            }
            // 只返回 review，确保前后端契约稳定且无多余字段。
            return JSONUtil.createObj().set("review", review).toString();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "周总结润色结果不是合法JSON，请重试。原始异常: " + e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private void normalizeAndValidateDrafts(List<MilestoneDraftVO> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 未生成有效里程碑，请重试");
        }

        for (int i = 0; i < drafts.size(); i++) {
            MilestoneDraftVO milestone = drafts.get(i);
            if (milestone == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 结果第" + (i + 1) + "个里程碑为空");
            }

            String milestoneName = safeTrim(milestone.getName());
            if (StrUtil.isBlank(milestoneName)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 结果第" + (i + 1) + "个里程碑名称为空");
            }
            if (milestoneName.length() > PROJECT_NAME_MAX_LEN) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "AI 结果第" + (i + 1) + "个里程碑名称超长，最多" + PROJECT_NAME_MAX_LEN + "字符");
            }
            milestone.setName(milestoneName);

            List<TaskDraftVO> tasks = milestone.getTasks();
            if (tasks == null || tasks.isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 结果第" + (i + 1) + "个里程碑缺少任务");
            }

            for (int j = 0; j < tasks.size(); j++) {
                TaskDraftVO task = tasks.get(j);
                if (task == null) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务为空");
                }

                String taskName = safeTrim(task.getName());
                if (StrUtil.isBlank(taskName)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务名称为空");
                }
                if (taskName.length() > TASK_TITLE_MAX_LEN) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务标题超长，最多"
                                    + TASK_TITLE_MAX_LEN + "字符");
                }
                task.setName(taskName);
            }
        }
    }

    private void logDraftLengthRisk(List<MilestoneDraftVO> drafts, String target, boolean detailed) {
        if (drafts == null || drafts.isEmpty()) {
            log.warn("AI任务拆解返回空草稿: target={}, detailed={}", target, detailed);
            return;
        }

        int milestoneCount = 0;
        int taskCount = 0;
        int milestoneNameOverLimitCount = 0;
        int taskNameOverLimitCount = 0;
        int blankMilestoneNameCount = 0;
        int blankTaskNameCount = 0;

        for (MilestoneDraftVO milestone : drafts) {
            milestoneCount++;
            if (milestone == null) {
                blankMilestoneNameCount++;
                continue;
            }

            String milestoneName = safeTrim(milestone.getName());
            if (StrUtil.isBlank(milestoneName)) {
                blankMilestoneNameCount++;
            } else if (milestoneName.length() > PROJECT_NAME_MAX_LEN) {
                milestoneNameOverLimitCount++;
            }

            List<TaskDraftVO> tasks = milestone.getTasks();
            if (tasks == null || tasks.isEmpty()) {
                continue;
            }
            for (TaskDraftVO task : tasks) {
                taskCount++;
                if (task == null) {
                    blankTaskNameCount++;
                    continue;
                }
                String taskName = safeTrim(task.getName());
                if (StrUtil.isBlank(taskName)) {
                    blankTaskNameCount++;
                } else if (taskName.length() > TASK_TITLE_MAX_LEN) {
                    taskNameOverLimitCount++;
                }
            }
        }

        if (milestoneNameOverLimitCount > 0 || taskNameOverLimitCount > 0
                || blankMilestoneNameCount > 0 || blankTaskNameCount > 0) {
            log.warn("AI任务拆解草稿存在导入风险: target={}, detailed={}, milestones={}, tasks={}, overMilestoneNames={}, overTaskNames={}, blankMilestoneNames={}, blankTaskNames={}",
                    target,
                    detailed,
                    milestoneCount,
                    taskCount,
                    milestoneNameOverLimitCount,
                    taskNameOverLimitCount,
                    blankMilestoneNameCount,
                    blankTaskNameCount);
        }
    }

    private String safeTrim(String text) {
        return text == null ? null : text.trim();
    }

    private String sanitizeJsonArrayText(String content) {
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

    private String sanitizeJsonObjectText(String content) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回内容为空");
        }

        String cleaned = content.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int startIndex = cleaned.indexOf('{');
        int endIndex = cleaned.lastIndexOf('}');
        if (startIndex >= 0 && endIndex > startIndex) {
            cleaned = cleaned.substring(startIndex, endIndex + 1);
        }
        return cleaned;
    }

    private String callAiWithFallback(String preferredModel, String systemPrompt, String userPrompt) {
        String primaryModel = resolveModel(preferredModel);
        String fallbackModel = safeTrim(aiProperties.getFallbackModel());

        try {
            return callAi(primaryModel, systemPrompt, userPrompt);
        } catch (BusinessException primaryException) {
            if (StrUtil.isBlank(fallbackModel) || StrUtil.equals(primaryModel, fallbackModel)) {
                throw primaryException;
            }
            log.warn("AI call failed on primary model, retrying with fallback model. primaryModel={}, fallbackModel={}",
                    primaryModel, fallbackModel, primaryException);
            return callAi(fallbackModel, systemPrompt, userPrompt);
        }
    }

    private String resolveModel(String preferredModel) {
        String model = StrUtil.isNotBlank(preferredModel) ? preferredModel.trim() : safeTrim(aiProperties.getModel());
        if (StrUtil.isBlank(model)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI configuration is incomplete, please check ai.model");
        }
        return model;
    }

    private String callAi(String model, String systemPrompt, String userPrompt) {
        String baseUrl = aiProperties.getBaseUrl();
        String apiKey = aiProperties.getApiKey();

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
