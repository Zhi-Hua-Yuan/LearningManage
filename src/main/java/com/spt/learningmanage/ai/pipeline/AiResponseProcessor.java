package com.spt.learningmanage.ai.pipeline;

/**
 * 将模型原始文本解析并校验为具体业务场景结果。
 *
 * @param <T> 场景结果类型
 */
@FunctionalInterface
public interface AiResponseProcessor<T> {

    T process(String rawContent);
}
