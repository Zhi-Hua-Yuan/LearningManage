package com.spt.learningmanage.service.impl.ai.support;

import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.utils.UserHolder;

public abstract class AiSceneSupport {

    protected Long currentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        return userId;
    }

    protected BusinessException toBusinessException(AiInvocationException exception) {
        ErrorCode errorCode = switch (exception.getFailureType()) {
            case CONFIG_ERROR -> ErrorCode.AI_CONFIG_ERROR;
            case TIMEOUT -> ErrorCode.AI_REQUEST_TIMEOUT;
            case INVALID_RESPONSE -> ErrorCode.AI_RESPONSE_INVALID;
            case FEATURE_DISABLED -> ErrorCode.AI_DISABLED;
            case CONCURRENCY_LIMIT -> ErrorCode.AI_CONCURRENCY_LIMIT;
            case CONTENT_BLOCKED -> ErrorCode.AI_CONTENT_BLOCKED;
            default -> ErrorCode.AI_SERVICE_UNAVAILABLE;
        };
        return new BusinessException(errorCode, exception.getSafeMessage());
    }

    protected String safeTrim(String text) {
        return text == null ? null : text.trim();
    }

    protected int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
