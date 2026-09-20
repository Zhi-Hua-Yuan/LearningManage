package com.spt.learningmanage.client.ai;

import com.spt.learningmanage.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * AI 调用的超时口径（唯一来源）。
 *
 * <p>两个传输适配器（{@code LegacyAiChatAdapter} 与 {@code SpringAiChatAdapter}）
 * 必须对同一份配置得出同一组超时值，否则「切换适配器」会隐式改变线上行为。
 * 因此把默认值、合法区间与回退策略集中在这里，而不是各自实现一份。</p>
 */
public final class AiTimeoutPolicy {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    public static final int DEFAULT_READ_TIMEOUT_MS = 60000;

    private static final int CONNECT_TIMEOUT_MIN_MS = 1000;

    private static final int CONNECT_TIMEOUT_MAX_MS = 30000;

    private static final int READ_TIMEOUT_MIN_MS = 5000;

    private static final int READ_TIMEOUT_MAX_MS = 300000;

    private static final Logger log = LoggerFactory.getLogger(AiTimeoutPolicy.class);

    private AiTimeoutPolicy() {
    }

    /**
     * 建连超时。非法配置（越界或缺失）回退到默认值并告警。
     */
    public static int connectTimeoutMs(AiProperties aiProperties) {
        return normalize(
                aiProperties.getConnectTimeoutMs(),
                DEFAULT_CONNECT_TIMEOUT_MS,
                CONNECT_TIMEOUT_MIN_MS,
                CONNECT_TIMEOUT_MAX_MS,
                "connectTimeoutMs"
        );
    }

    /**
     * 读超时。非法配置（越界或缺失）回退到默认值并告警。
     *
     * <p>注意这里是「配置上限」，不是单次调用的实际值：legacy 适配器还会与
     * 总时限取最小值，Spring AI 适配器的读超时在装配期固定，由治理层总时限兜底。</p>
     */
    public static int readTimeoutMs(AiProperties aiProperties) {
        return normalize(
                aiProperties.getReadTimeoutMs(),
                DEFAULT_READ_TIMEOUT_MS,
                READ_TIMEOUT_MIN_MS,
                READ_TIMEOUT_MAX_MS,
                "readTimeoutMs"
        );
    }

    /**
     * Converts the absolute governance deadline into a positive socket timeout.
     * Keeping the conversion here makes legacy and Spring AI use the same
     * truncation and lower-bound semantics.
     */
    public static int remainingTimeoutMs(long deadlineNanos) {
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        return (int) Math.max(1L, remainingMillis);
    }

    private static int normalize(Integer configured,
                                 int defaultValue,
                                 int minValue,
                                 int maxValue,
                                 String propertyName) {
        if (configured == null || configured < minValue || configured > maxValue) {
            log.warn("AI 超时配置不合法，使用默认值: property={}, configured={}, default={}",
                    propertyName, configured, defaultValue);
            return defaultValue;
        }
        return configured;
    }
}
