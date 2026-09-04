package com.spt.learningmanage.service.impl.ai.scene;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.ai.pipeline.AiExecutionCommand;
import com.spt.learningmanage.ai.pipeline.AiExecutionResult;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.ai.AiTaskOrderItemVO;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.scene.TodayOrderAiService;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.support.AiSceneSupport;
import com.spt.learningmanage.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TodayOrderAiServiceImpl extends AiSceneSupport implements TodayOrderAiService {

    private static final Logger log = LoggerFactory.getLogger(TodayOrderAiServiceImpl.class);
    private static final int TASK_PRIORITY_MIN = 0;
    private static final int TASK_PRIORITY_MAX = 3;
    private static final int TODAY_ORDER_LIMIT_MAX = 50;
    private static final int TODAY_ORDER_LIMIT_DEFAULT = 20;

    private final TaskMapper taskMapper;
    private final AiInvocationPipeline aiInvocationPipeline;
    private final PermissionService permissionService;
    private final AiModelSelector modelSelector;
    private final AiJsonResponseSanitizer jsonSanitizer;

    public TodayOrderAiServiceImpl(TaskMapper taskMapper,
                                   AiInvocationPipeline aiInvocationPipeline,
                                   PermissionService permissionService,
                                   AiModelSelector modelSelector,
                                   AiJsonResponseSanitizer jsonSanitizer) {
        this.taskMapper = taskMapper;
        this.aiInvocationPipeline = aiInvocationPipeline;
        this.permissionService = permissionService;
        this.modelSelector = modelSelector;
        this.jsonSanitizer = jsonSanitizer;
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

        String userPrompt = buildTodayOrderUserPrompt(tasks, strategy, now, today, zoneId);
        AiExecutionCommand command = new AiExecutionCommand(
                currentUserId,
                modelSelector.breakdownModel(),
                AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                userPrompt,
                "AI 今日任务排序结果格式异常"
        );
        AiExecutionResult<AiTodayOrderVO> execution = aiInvocationPipeline.execute(
                command,
                rawContent -> parseAndValidateTodayOrderResult(rawContent, tasks, strategy, now),
                failure -> {
                    log.warn("AI今日任务排序失败，回退规则排序: userId={}, today={}, strategy={}, type={}",
                            currentUserId, today, strategy, failure.failureType(), failure.cause());
                    result.setFallbackUsed(true);
                    result.setItems(fallbackByRule(tasks, strategy, now));
                    return result;
                }
        );
        AiTodayOrderVO ordered = execution.data();
        ordered.setGeneratedAt(LocalDateTime.now(zoneId).toString());
        ordered.setFallbackUsed(execution.degraded());
        return ordered;
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
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "timezone 非法，请使用 IANA 时区，如 Asia/Shanghai");
        }
    }

    private LocalDateTime resolveNow(String nowText, ZoneId zoneId) {
        if (StrUtil.isBlank(nowText)) {
            return LocalDateTime.now(zoneId);
        }
        try {
            return LocalDateTime.parse(nowText.trim());
        } catch (Exception exception) {
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
            if (taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 至少包含一个有效的正整数ID");
            }
            Set<Long> uniqueIds = permissionService.requireAllTasksReadable(userId, taskIds);
            List<Task> selectedTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                    .in(Task::getId, uniqueIds)
                    .eq(Task::getIsDelete, 0)
                    .eq(Task::getDueDate, today)
                    .eq(Task::getStatus, 0)
                    .orderByDesc(Task::getPriority)
                    .orderByAsc(Task::getCreateTime, Task::getId));
            if (selectedTasks.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 中无可推荐的今日到期未完成任务");
            }
            Set<Long> selectedIds = selectedTasks.stream().map(Task::getId).collect(Collectors.toSet());
            if (!selectedIds.containsAll(uniqueIds) || selectedIds.size() != uniqueIds.size()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 中存在不满足今日未完成条件的任务，不能部分放行");
            }
            return selectedTasks.stream().limit(limit).toList();
        }

        List<Task> candidates = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeUserId, userId)
                .eq(Task::getDueDate, today)
                .eq(Task::getStatus, 0)
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getCreateTime, Task::getId));
        Set<Long> readableIds = candidates.isEmpty()
                ? Set.of()
                : permissionService.filterReadableTaskIds(userId, candidates.stream().map(Task::getId).toList());
        return candidates.stream().filter(task -> readableIds.contains(task.getId())).limit(limit).toList();
    }

    private String buildTodayOrderUserPrompt(List<Task> tasks, String strategy, LocalDateTime now,
                                             LocalDate today, ZoneId zoneId) {
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

    private AiTodayOrderVO parseAndValidateTodayOrderResult(String aiRawContent, List<Task> sourceTasks,
                                                            String strategy, LocalDateTime now) {
        JSONObject resultObj = JSONUtil.parseObj(jsonSanitizer.sanitizeObject(aiRawContent));
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
            orderItems.add(buildOrderItem(taskMap.get(taskId), i + 1, difficulty, cost, benefit,
                    estimatedMinutes, reason, strategy, now));
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
                .sorted(Comparator.comparing(Task::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
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

    private AiTaskOrderItemVO buildOrderItem(Task task, int rank, int difficulty, int cost, int benefit,
                                             int estimatedMinutes, String reason, String strategy,
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
        item.setScore(calcScore(task.getPriority(), difficulty, cost, benefit, estimatedMinutes,
                strategy, now, task.getDueDate()));
        return item;
    }

    private int calcScore(Integer priority, Integer difficulty, Integer cost, Integer benefit,
                          Integer estimatedMinutes, String strategy, LocalDateTime now, LocalDate dueDate) {
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
}
