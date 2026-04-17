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
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.ai.AiTaskOrderItemVO;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.model.vo.milestone.TaskDraftVO;
import com.spt.learningmanage.service.AiService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final int TASK_PRIORITY_MIN = 0;
    private static final int TASK_PRIORITY_MAX = 3;
    private static final int TODAY_ORDER_LIMIT_MAX = 50;
    private static final int TODAY_ORDER_LIMIT_DEFAULT = 20;

    private static final String TODAY_ORDER_SYSTEM_PROMPT = "你是任务调度助手。"
            + "请基于任务难度、成本、效益、优先级与当前时间，给出今天任务的推荐完成顺序。"
            + "只输出合法JSON对象，不要Markdown，不要解释文字。"
            + "输出结构严格为："
            + "{\"strategy\":\"balanced\",\"items\":[{\"taskId\":1,\"difficulty\":3,\"cost\":2,\"benefit\":5,\"estimatedMinutes\":30,\"reason\":\"...\"}]}。"
            + "要求：difficulty/cost/benefit 必须是1-5整数；estimatedMinutes为10-240整数；items必须覆盖所有输入taskId且不重复。";

    private static final String TASK_BREAKDOWN_SYSTEM_PROMPT_DEFAULT = "你是一名资深项目经理与学习规划顾问。"
            + "请根据用户目标、周期和补充描述，输出可执行的里程碑与任务拆解。"
            + "硬性要求："
            + "1) 只输出纯 JSON 数组，不要 Markdown，不要解释文字；"
            + "2) 里程碑 2-4 个，按推进顺序组织；"
            + "3) 每个里程碑 2-5 个任务；"
            + "4) 每个任务对象必须且仅包含 name、priority、dueDate 三个字段；"
            + "5) priority 必须是 0-3 的整数（0=无/稍后，1=低，2=中，3=高）；"
            + "6) dueDate 必须是绝对日期，格式 yyyy-MM-dd，不允许相对日期；"
            + "7) 里程碑 name 长度不超过100，任务 name 长度不超过60，避免重复和空泛。"
            + "严格输出结构："
            + "[{\"name\":\"里程碑\",\"tasks\":[{\"name\":\"任务1\",\"priority\":2,\"dueDate\":\"2026-04-20\"}]}]";

    private static final String TASK_BREAKDOWN_SYSTEM_PROMPT_DETAILED = "你是一名资深项目经理与学习规划顾问。"
            + "现在需要输出更细颗粒度、可落地的执行计划。"
            + "硬性要求："
            + "1) 只输出纯 JSON 数组，不要 Markdown，不要解释文字；"
            + "2) 里程碑 3-4 个，必须体现阶段递进关系；"
            + "3) 每个里程碑 4-6 个任务，任务要具体、可执行、可检查；"
            + "4) 每个任务对象必须且仅包含 name、priority、dueDate 三个字段；"
            + "5) priority 必须是 0-3 的整数（0=无/稍后，1=低，2=中，3=高）；"
            + "6) dueDate 必须是绝对日期，格式 yyyy-MM-dd，不允许相对日期；"
            + "7) 优先输出有产出物的任务，避免重复和空泛。"
            + "8) 里程碑 name 长度不超过100，任务 name 长度不超过60。"
            + "严格输出结构："
            + "[{\"name\":\"里程碑\",\"tasks\":[{\"name\":\"任务1\",\"priority\":3,\"dueDate\":\"2026-04-20\"}]}]";

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

        String today = LocalDate.now().toString();
        String userPrompt = String.format("目标：%s，周期：%s，今天日期：%s。", normalizedTarget, duration.trim(), today);
        if (StrUtil.isNotBlank(description)) {
            userPrompt = userPrompt + String.format("补充描述：%s。", description.trim());
        }

        String systemPrompt = detailed ? TASK_BREAKDOWN_SYSTEM_PROMPT_DETAILED : TASK_BREAKDOWN_SYSTEM_PROMPT_DEFAULT;
        String aiRawContent = callAiWithFallback(aiProperties.getBreakdownModel(), systemPrompt, userPrompt);
        String jsonText = sanitizeJsonArrayText(aiRawContent);

        try {
            JSONArray jsonArray = JSONUtil.parseArray(jsonText);
            List<MilestoneDraftVO> result = JSONUtil.toList(jsonArray, MilestoneDraftVO.class);
            normalizeAndValidateDrafts(result);
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

    @Override
    public AiTodayOrderVO recommendTodayOrder(AiTodayOrderRequest request) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录状态已失效，请重新登录");
        }

        AiTodayOrderRequest safeRequest = request == null ? new AiTodayOrderRequest() : request;
        String strategy = normalizeStrategy(safeRequest.getStrategy());
        ZoneId zoneId = resolveZoneId(safeRequest.getTimezone());
        LocalDateTime now = resolveNow(safeRequest.getNow(), zoneId);
        int limit = resolveLimit(safeRequest.getLimit());
        LocalDate today = now.toLocalDate();

        List<Task> tasks = loadTodayTodoTasks(currentUserId, safeRequest, today, limit);

        AiTodayOrderVO result = new AiTodayOrderVO();
        result.setStrategy(strategy);
        result.setGeneratedAt(LocalDateTime.now(zoneId).toString());
        if (tasks.isEmpty()) {
            result.setFallbackUsed(false);
            result.setItems(List.of());
            return result;
        }

        try {
            String userPrompt = buildTodayOrderUserPrompt(tasks, strategy, now, today, zoneId);
            String aiRawContent = callAiWithFallback(aiProperties.getBreakdownModel(), TODAY_ORDER_SYSTEM_PROMPT, userPrompt);
            AiTodayOrderVO aiResult = parseAndValidateTodayOrderResult(aiRawContent, tasks, strategy, now);
            aiResult.setGeneratedAt(LocalDateTime.now(zoneId).toString());
            aiResult.setFallbackUsed(false);
            return aiResult;
        } catch (Exception e) {
            log.warn("AI今日任务排序失败，回退规则排序: userId={}, today={}, strategy={}",
                    currentUserId, today, strategy, e);
            result.setFallbackUsed(true);
            result.setItems(fallbackByRule(tasks, strategy, now));
            return result;
        }
    }

    private String normalizeStrategy(String strategy) {
        if (StrUtil.isBlank(strategy)) {
            return "balanced";
        }
        String normalized = strategy.trim().toLowerCase(Locale.ROOT);
        if ("balanced".equals(normalized) || "benefit_first".equals(normalized) || "quick_win".equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "strategy 仅支持 balanced、benefit_first、quick_win");
    }

    private ZoneId resolveZoneId(String timezone) {
        if (StrUtil.isBlank(timezone)) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "timezone 非法，请使用 IANA 时区，如 Asia/Shanghai");
        }
    }

    private LocalDateTime resolveNow(String nowText, ZoneId zoneId) {
        if (StrUtil.isBlank(nowText)) {
            return LocalDateTime.now(zoneId);
        }
        try {
            return LocalDateTime.parse(nowText.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "now 非法，请使用 ISO-8601 时间格式，如 2026-04-17T10:30:00");
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return TODAY_ORDER_LIMIT_DEFAULT;
        }
        return Math.min(limit, TODAY_ORDER_LIMIT_MAX);
    }

    private List<Task> loadTodayTodoTasks(Long userId, AiTodayOrderRequest request, LocalDate today, int limit) {
        List<Long> taskIds = request.getTaskIds();
        if (taskIds != null && !taskIds.isEmpty()) {
            Set<Long> uniqueIds = taskIds.stream()
                    .filter(id -> id != null && id > 0)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (uniqueIds.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 至少包含一个有效的正整数ID");
            }

            List<Task> selectedTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                    .in(Task::getId, uniqueIds)
                    .eq(Task::getUserId, userId)
                    .eq(Task::getDueDate, today)
                    .eq(Task::getStatus, 0)
                    .orderByDesc(Task::getPriority)
                    .orderByAsc(Task::getCreateTime, Task::getId));

            if (selectedTasks.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 中无可推荐的今日到期未完成任务");
            }
            return selectedTasks.stream().limit(limit).toList();
        }

        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .eq(Task::getDueDate, today)
                .eq(Task::getStatus, 0)
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getCreateTime, Task::getId)
                .last("limit " + limit));
    }

    private String buildTodayOrderUserPrompt(List<Task> tasks, String strategy, LocalDateTime now, LocalDate today, ZoneId zoneId) {
        JSONArray taskContext = JSONUtil.createArray();
        for (Task task : tasks) {
            taskContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId())
                    .set("title", task.getTitle())
                    .set("description", task.getDescription())
                    .set("priority", task.getPriority())
                    .set("dueDate", task.getDueDate())
                    .set("createTime", task.getCreateTime()));
        }

        return "日期：" + today
                + "\n当前时间：" + now
                + "\n时区：" + zoneId
                + "\n排序策略：" + strategy
                + "\n请覆盖全部任务，不要遗漏，不要重复。"
                + "\n任务列表(JSON)：" + taskContext;
    }

    private AiTodayOrderVO parseAndValidateTodayOrderResult(String aiRawContent,
                                                            List<Task> sourceTasks,
                                                            String strategy,
                                                            LocalDateTime now) {
        String cleanedText = sanitizeJsonObjectText(aiRawContent);
        JSONObject resultObj = JSONUtil.parseObj(cleanedText);
        JSONArray items = resultObj.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果缺少 items");
        }
        if (items.size() != sourceTasks.size()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果任务数量与输入不一致");
        }

        Map<Long, Task> taskMap = sourceTasks.stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        Set<Long> seenTaskIds = new HashSet<>();
        List<AiTaskOrderItemVO> orderItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果存在空任务项");
            }
            Long taskId = item.getLong("taskId");
            if (taskId == null || !taskMap.containsKey(taskId)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果包含无效 taskId");
            }
            if (!seenTaskIds.add(taskId)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果包含重复 taskId");
            }

            Integer difficulty = item.getInt("difficulty");
            Integer cost = item.getInt("cost");
            Integer benefit = item.getInt("benefit");
            Integer estimatedMinutes = item.getInt("estimatedMinutes");
            if (!inRange(difficulty, 1, 5) || !inRange(cost, 1, 5) || !inRange(benefit, 1, 5)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果 difficulty/cost/benefit 超出范围");
            }
            if (!inRange(estimatedMinutes, 10, 240)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果 estimatedMinutes 超出范围");
            }
            String reason = safeTrim(item.getStr("reason"));
            if (StrUtil.isBlank(reason)) {
                reason = "AI建议优先处理";
            }

            orderItems.add(buildOrderItem(taskMap.get(taskId), i + 1, difficulty, cost, benefit, estimatedMinutes, reason, strategy, now));
        }

        if (seenTaskIds.size() != taskMap.size()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果未覆盖全部任务");
        }

        AiTodayOrderVO result = new AiTodayOrderVO();
        result.setStrategy(normalizeStrategy(resultObj.getStr("strategy", strategy)));
        result.setItems(orderItems);
        return result;
    }

    private boolean inRange(Integer value, int min, int max) {
        return value != null && value >= min && value <= max;
    }

    private List<AiTaskOrderItemVO> fallbackByRule(List<Task> tasks, String strategy, LocalDateTime now) {
        List<Task> sorted = tasks.stream()
                .sorted(Comparator
                        .comparing(Task::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Task::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Task::getId))
                .toList();

        List<AiTaskOrderItemVO> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Task task = sorted.get(i);
            int priority = task.getPriority() == null ? 0 : task.getPriority();
            int difficulty = priority >= 3 ? 3 : 2;
            int cost = priority >= 2 ? 3 : 2;
            int benefit = clamp(priority + 2, 1, 5);
            int estimatedMinutes = clamp(20 + difficulty * 15, 10, 240);
            result.add(buildOrderItem(task, i + 1, difficulty, cost, benefit, estimatedMinutes,
                    "规则兜底：按优先级与创建时间排序", strategy, now));
        }
        return result;
    }

    private AiTaskOrderItemVO buildOrderItem(Task task,
                                             int rank,
                                             int difficulty,
                                             int cost,
                                             int benefit,
                                             int estimatedMinutes,
                                             String reason,
                                             String strategy,
                                             LocalDateTime now) {
        AiTaskOrderItemVO item = new AiTaskOrderItemVO();
        item.setTaskId(task.getId());
        item.setTitle(task.getTitle());
        item.setRank(rank);
        item.setDifficulty(difficulty);
        item.setCost(cost);
        item.setBenefit(benefit);
        item.setEstimatedMinutes(estimatedMinutes);
        item.setReason(reason);
        item.setScore(calcScore(task.getPriority(), difficulty, cost, benefit, estimatedMinutes, strategy, now, task.getDueDate()));
        return item;
    }

    private int calcScore(Integer priority,
                          Integer difficulty,
                          Integer cost,
                          Integer benefit,
                          Integer estimatedMinutes,
                          String strategy,
                          LocalDateTime now,
                          LocalDate dueDate) {
        int priorityValue = clamp(priority == null ? 0 : priority, TASK_PRIORITY_MIN, TASK_PRIORITY_MAX);
        int d = clamp(difficulty, 1, 5);
        int c = clamp(cost, 1, 5);
        int b = clamp(benefit, 1, 5);
        int m = clamp(estimatedMinutes, 10, 240);

        double score;
        switch (strategy) {
            case "benefit_first" -> score = b * 24 + priorityValue * 8 - c * 7 - d * 5 - m / 12.0;
            case "quick_win" -> score = b * 16 + priorityValue * 8 - c * 10 - d * 10 - m / 6.0;
            default -> score = b * 20 + priorityValue * 10 - c * 8 - d * 6 - m / 10.0;
        }

        if (dueDate != null && dueDate.equals(now.toLocalDate())) {
            score += 5;
        }
        return clamp((int) Math.round(score), 0, 100);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

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
                Integer priority = task.getPriority();
                if (priority == null || priority < TASK_PRIORITY_MIN || priority > TASK_PRIORITY_MAX) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务优先级非法，需为0-3整数");
                }

                String dueDate = safeTrim(task.getDueDate());
                if (StrUtil.isBlank(dueDate)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务截止日期为空");
                }
                LocalDate parsedDueDate;
                try {
                    parsedDueDate = LocalDate.parse(dueDate);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务截止日期格式非法，需为yyyy-MM-dd");
                }

                task.setName(taskName);
                task.setPriority(priority);
                task.setDueDate(parsedDueDate.toString());
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
        int invalidPriorityCount = 0;
        int blankDueDateCount = 0;
        int invalidDueDateCount = 0;

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

                Integer priority = task.getPriority();
                if (priority == null || priority < TASK_PRIORITY_MIN || priority > TASK_PRIORITY_MAX) {
                    invalidPriorityCount++;
                }

                String dueDate = safeTrim(task.getDueDate());
                if (StrUtil.isBlank(dueDate)) {
                    blankDueDateCount++;
                } else {
                    try {
                        LocalDate.parse(dueDate);
                    } catch (Exception e) {
                        invalidDueDateCount++;
                    }
                }
            }
        }

        if (milestoneNameOverLimitCount > 0 || taskNameOverLimitCount > 0
                || blankMilestoneNameCount > 0 || blankTaskNameCount > 0
                || invalidPriorityCount > 0 || blankDueDateCount > 0 || invalidDueDateCount > 0) {
            log.warn("AI任务拆解草稿存在导入风险: target={}, detailed={}, milestones={}, tasks={}, overMilestoneNames={}, overTaskNames={}, blankMilestoneNames={}, blankTaskNames={}, invalidPriority={}, blankDueDate={}, invalidDueDate={}",
                    target,
                    detailed,
                    milestoneCount,
                    taskCount,
                    milestoneNameOverLimitCount,
                    taskNameOverLimitCount,
                    blankMilestoneNameCount,
                    blankTaskNameCount,
                    invalidPriorityCount,
                    blankDueDateCount,
                    invalidDueDateCount);
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

