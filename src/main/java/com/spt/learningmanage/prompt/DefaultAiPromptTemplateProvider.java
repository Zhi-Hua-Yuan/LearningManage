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

        register(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT, "你是一名资深项目经理与学习规划顾问。"
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
                + "[{\"name\":\"里程碑\",\"tasks\":[{\"name\":\"任务1\",\"priority\":2,\"dueDate\":\"2026-04-20\"}]}]");

        register(AiPromptCodeEnum.TASK_BREAKDOWN_DETAILED, "你是一名资深项目经理与学习规划顾问。"
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
                + "[{\"name\":\"里程碑\",\"tasks\":[{\"name\":\"任务1\",\"priority\":3,\"dueDate\":\"2026-04-20\"}]}]");

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
