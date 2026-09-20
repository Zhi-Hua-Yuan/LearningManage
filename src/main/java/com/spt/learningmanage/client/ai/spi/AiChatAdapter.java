package com.spt.learningmanage.client.ai.spi;

import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;

/**
 * 模型传输协议适配器 SPI：把「治理」与「传输协议」分开。
 *
 * <p>本接口只负责一次 chat 请求的传输侧职责：请求体构造、上游调用、
 * 响应解析、HTTP 状态码与网络异常到 {@code AiFailureTypeEnum} 的映射。
 * 它<b>不做治理</b>——Feature Gate、脱敏、总时限、主备模型、Usage/Cost、
 * Bulkhead、Circuit Breaker、响应与命令一致性校验全部留在治理层
 * （即模型客户端的唯一实现，扮演 GovernedModelClient 角色）。</p>
 *
 * <p>实现类约束：不得依赖治理组件，不得注册 Tool，不得自行重试；
 * 重试只允许发生在治理层。治理层不知道 Spring AI 的存在，
 * 因此替换或删除任一适配器时治理层与业务层零改动。</p>
 */
public interface AiChatAdapter {

    /**
     * 执行一次上游模型调用。
     *
     * @param command 已完成命令校验、已脱敏、requestedModel 已归一化的命令
     * @param context 本次尝试的调度上下文（原始主模型、重试序号、兜底原因、总时限）
     * @return 传输层解析结果，其中 requestedModel / retryCount / fallbackUsed /
     *         fallbackReason 按 {@code context} 回填；attempts 由治理层补齐
     * @throws com.spt.learningmanage.exception.AiInvocationException 传输或解析失败
     */
    AiChatResult chat(AiChatCommand command, AiChatDispatchContext context);
}
