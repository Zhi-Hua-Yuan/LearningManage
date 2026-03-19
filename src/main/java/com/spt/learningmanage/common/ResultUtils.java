package com.spt.learningmanage.common;

import com.spt.learningmanage.exception.ErrorCode;

public final class ResultUtils {
    private ResultUtils() {
    }

    public static <T> BaseResponse<T> ok() {
        return ok(null);
    }

    public static <T> BaseResponse<T> ok(T data) {
        return new BaseResponse<>(ErrorCode.SUCCESS, data);
    }

    public static <T> BaseResponse<T> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    public static <T> BaseResponse<T> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode, message);
    }
}
