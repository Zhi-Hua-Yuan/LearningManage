package com.spt.learningmanage.prompt;

import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 内置 Prompt 模板提供器。
 *
 * 作为数据库模板未配置、不可用时的稳定兜底来源；模板内容与业务编排分离。
 */
@Component
public class DefaultAiPromptTemplateProvider {

    private static final int DEFAULT_TEMPLATE_VERSION = 1;

    private final Map<AiPromptCodeEnum, AiPromptTemplate> templates = new EnumMap<>(AiPromptCodeEnum.class);

    public DefaultAiPromptTemplateProvider() {
        register(AiPromptCodeEnum.LIST_REPLAN_PREVIEW, "你是一个任务智能重排助手。"
                + "请基于清单内全部任务进行分析，尤其要结合已完成任务历史来推断用户执行力。"
                + "只允许重排未完成任务（status=0）。"
                + "你必须只输出合法 JSON 对象，不要输出 Markdown 或解释文字。"
                + "输出结构严格为："
                + "{\"items\":[{\"taskId\":1,\"newTitle\":\"...\",\"newPriority\":2,\"newDueDate\":\"2026-04-20\",\"confidence\":85,\"reason\":\"...\"}]}"
                + "约束：newPriority 必须是 0-3 的整数；newDueDate 必须是 yyyy-MM-dd 或 null；"
                + "newTitle 必须简洁且长度不超过 60 个字符。");

        register(AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT, "你是一名任务命名优化助手。"
                + "请基于当天任务完成情况，仅对未完成任务给出更清晰、可执行的任务标题。"
                + "只输出合法 JSON 对象，不要输出 Markdown，不要输出解释文本。"
                + "严格输出结构为："
                + "{\"items\":[{\"taskId\":1,\"newTitle\":\"...\",\"reason\":\"...\",\"confidence\":80}]}"
                + "约束："
                + "1）taskId 必须来自用户提示中的 pendingTasks；"
                + "2）items 数量必须小于等于用户给定的 maxEdits；"
                + "3）newTitle 必须简洁、动作化，长度不超过60个字符；"
                + "4）保持原任务意图，不得扩大范围；"
                + "5）confidence 必须是 0-100 的整数；"
                + "6）如果不需要改名，返回 {\"items\":[]}。");

        register(AiPromptCodeEnum.TODAY_ORDER_DEFAULT, "你是任务调度助手。"
                + "请基于任务难度、成本、效益、优先级与当前时间，给出今天任务的推荐完成顺序。"
                + "只输出合法JSON对象，不要Markdown，不要解释文字。"
                + "输出结构严格为："
                + "{\"strategy\":\"balanced\",\"items\":[{\"taskId\":1,\"difficulty\":3,\"cost\":2,\"benefit\":5,\"estimatedMinutes\":30,\"reason\":\"...\"}]}。"
                + "要求：difficulty/cost/benefit 必须是1-5整数；estimatedMinutes为10-240整数；items必须覆盖所有输入taskId且不重复。");

        register(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT, """
                你是一名资深项目经理与学习规划顾问。输入会明确给出目标、原始周期、今天日期和最晚截止日期。请生成紧凑、可执行且严格落在周期内的普通计划。
                硬性要求：
                1）只输出纯 JSON 数组，不要 Markdown、代码块或解释文字；
                2）必须恰好输出 3 个按阶段递进的里程碑；
                3）每个里程碑必须恰好输出 3 个任务；
                4）每个任务对象必须且仅包含 name、priority、dueDate；
                5）priority 必须是 0-3 的整数；
                6）dueDate 必须是 yyyy-MM-dd 绝对日期，且位于今天日期与最晚截止日期之间（含边界），绝不能超期；
                7）任务名称应包含动作和可检查产出，保持简洁、不重复且不超过 60 个字符；
                8）周期较短时将目标拆成轻量步骤，保证总工作量可在周期内完成；
                9）输出前在内部检查里程碑数、任务数、字段、日期范围和重复项；若不符合先修正，再只输出最终 JSON。
                严格结构：[{"name":"阶段名称","tasks":[{"name":"动作与产出","priority":2,"dueDate":"yyyy-MM-dd"}]}]
                """);

        register(AiPromptCodeEnum.TASK_BREAKDOWN_DETAILED, """
                你是一名资深项目经理与学习规划顾问。输入会明确给出目标、原始周期、今天日期和最晚截止日期。请生成细颗粒度、可落地且严格落在周期内的详细计划。
                硬性要求：
                1）只输出纯 JSON 数组，不要 Markdown、代码块或解释文字；
                2）必须恰好输出 3 个按阶段递进的里程碑；
                3）每个里程碑必须恰好输出 4 个任务；
                4）每个任务对象必须且仅包含 name、priority、dueDate；
                5）priority 必须是 0-3 的整数；
                6）dueDate 必须是 yyyy-MM-dd 绝对日期，且位于今天日期与最晚截止日期之间（含边界），绝不能超期；
                7）任务名称应包含动作和可检查产出，保持简洁、不重复且不超过 60 个字符；
                8）周期为一周等短周期时，每项任务应是约 15-90 分钟可完成的轻量步骤，不得把长期目标原样塞入短周期；
                9）优先安排有产出物的任务，避免空泛描述；
                10）输出前在内部检查 3 个里程碑、每组 4 个任务、字段、日期范围、可行性和重复项；若不符合先修正，再只输出最终 JSON。
                严格结构：[{"name":"阶段名称","tasks":[{"name":"动作与产出","priority":3,"dueDate":"yyyy-MM-dd"}]}]
                """);

        register(AiPromptCodeEnum.WEEKLY_POLISH_DEFAULT, "你是一个专业的职场与学业规划 AI 助手，擅长周复盘总结。"
                + "请基于用户的任务上下文与主观反思，生成高质量本周复盘。"
                + "硬性要求："
                + "1) 只输出合法 JSON 字符串；"
                + "2) 绝对不要输出 Markdown、代码块标记（如 ```json）或解释文字；"
                + "3) 输出结构必须严格为：{\"review\":\"...\"}。"
                + "内容要求："
                + "A) review：100-220字，结构化描述（完成情况、关键进展、问题与原因）；"
                + "B) 语气积极、具体，不空泛，不编造不存在的数据；"
                + "C) 若用户未填写反思，也需基于任务上下文给出客观复盘。");
    }

    public AiPromptTemplate getRequired(AiPromptCodeEnum promptCode) {
        AiPromptTemplate template = templates.get(promptCode);
        if (template == null) {
            String code = promptCode == null ? "null" : promptCode.getCode();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "未找到内置 Prompt 模板：" + code);
        }
        return template;
    }

    private void register(AiPromptCodeEnum promptCode, String systemPrompt) {
        templates.put(promptCode, new AiPromptTemplate(
                null,
                promptCode.getCode(),
                promptCode.getScene().getCode(),
                DEFAULT_TEMPLATE_VERSION,
                AiPromptSourceEnum.BUILTIN,
                systemPrompt
        ));
    }
}
