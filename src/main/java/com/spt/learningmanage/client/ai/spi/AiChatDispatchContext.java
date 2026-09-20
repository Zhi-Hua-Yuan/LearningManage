package com.spt.learningmanage.client.ai.spi;

import com.spt.learningmanage.constant.AiFailureTypeEnum;

/**
 * 一次模型尝试的调度上下文，由治理层构造后传入适配器。
 *
 * <p>适配器需要这些治理侧输入才能保持既有行为：</p>
 * <ul>
 *   <li>{@code deadlineNanos} —— 逻辑调用总时限的剩余预算。适配器把 socket
 *       read timeout 收敛到「配置值」与「剩余预算」的较小值，保证单次 HTTP
 *       调用不会越过总时限（这条与 legacy 行为逐位一致，不能放宽）。</li>
 *   <li>{@code requestedModel} —— 原始主模型，用于失败归因并回写到
 *       {@code AiChatResult.requestedModel}（兜底尝试时它仍是主模型，
 *       与 {@code command.requestedModel()} 不同）。</li>
 *   <li>{@code retryCount} / {@code fallbackReason} —— 回写审计字段，
 *       供 {@code AiCallLogService} 记录 retryCount 与 fallbackUsed/fallbackReason。</li>
 * </ul>
 *
 * @param requestedModel 原始主模型（不是本次尝试模型）
 * @param retryCount     0 = 主模型尝试；1 = 兜底模型尝试
 * @param fallbackReason 触发兜底的主模型失败类型；主模型尝试时为 {@code null}
 * @param deadlineNanos  总时限的绝对纳秒时刻，基准为 {@link System#nanoTime()}
 */
public record AiChatDispatchContext(
        String requestedModel,
        int retryCount,
        AiFailureTypeEnum fallbackReason,
        long deadlineNanos
) {
}
