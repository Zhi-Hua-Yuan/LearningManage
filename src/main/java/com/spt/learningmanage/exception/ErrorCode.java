package com.spt.learningmanage.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    OK(0, "OK"),
    VALIDATION_ERROR(400, "Validation error"),
    NOT_FOUND(404, "Not found"),
    INTERNAL_ERROR(500, "Internal server error"),
    BUSINESS_ERROR(1000, "Business error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
