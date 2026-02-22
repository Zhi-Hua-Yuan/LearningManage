package com.spt.learningmanage.common;

import com.spt.learningmanage.exception.ErrorCode;
import lombok.Data;

@Data
public class BaseResponse<T> {
    private int code;
    private String message;
    private T data;

    public BaseResponse() {
    }

    public BaseResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public BaseResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public BaseResponse(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BaseResponse(ErrorCode errorCode, T data) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.data = data;
    }

    public BaseResponse(ErrorCode errorCode, String message) {
        this.code = errorCode.getCode();
        this.message = message;
    }

    public BaseResponse(ErrorCode errorCode, String message, T data) {
        this.code = errorCode.getCode();
        this.message = message;
        this.data = data;
    }
}
