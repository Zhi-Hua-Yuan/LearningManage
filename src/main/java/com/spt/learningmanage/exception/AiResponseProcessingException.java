package com.spt.learningmanage.exception;

/**
 * 模型调用已经返回内容，但场景解析或业务校验失败。
 */
public class AiResponseProcessingException extends RuntimeException {

    private final String safeMessage;

    public AiResponseProcessingException(String safeMessage, Throwable cause) {
        super(buildInternalMessage(safeMessage), cause);
        this.safeMessage = safeMessage;
    }

    public String getSafeMessage() {
        return safeMessage;
    }

    private static String buildInternalMessage(String safeMessage) {
        if (safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException("安全错误信息不能为空");
        }
        return "AI 响应处理失败：" + safeMessage;
    }
}
