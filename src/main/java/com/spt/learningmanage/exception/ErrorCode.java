package com.spt.learningmanage.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    /**
     * 基础通用错误码 (400xx: 客户端错误, 401xx: 鉴权错误, 500xx: 服务端错误)
     */
    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),

    /**
     * 项目相关 (1xxxx)
     */
    PROJECT_NAME_EMPTY(10001, "项目名称不能为空"),
    PROJECT_ALREADY_EXISTS(10002, "项目已存在"),
    PROJECT_NOT_FOUND(10003, "项目不存在"),

    /**
     * 用户相关 (2xxxx)
     */
    USER_NOT_FOUND(20001, "用户不存在"),
    ACCOUNT_ALREADY_EXISTS(20002, "账号已存在"),
    PASSWORD_ERROR(20003, "密码错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
