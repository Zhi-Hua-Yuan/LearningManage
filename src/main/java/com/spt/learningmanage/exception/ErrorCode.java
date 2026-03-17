package com.spt.learningmanage.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    OK(0, "成功"),
    VALIDATION_ERROR(400, "参数校验失败"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),
    BUSINESS_ERROR(1000, "业务异常"),
    PROJECT_NAME_EMPTY(1001, "项目名称不能为空"),
    PROJECT_ALREADY_EXISTS(1002, "项目已存在"),
    PROJECT_NOT_FOUND(1003, "项目不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
