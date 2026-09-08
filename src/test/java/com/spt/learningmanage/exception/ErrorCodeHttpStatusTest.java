package com.spt.learningmanage.exception;

import com.spt.learningmanage.common.BaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeHttpStatusTest {

    @Test
    void everyErrorCodeHasAnExplicitSupportedHttpStatus() {
        EnumSet<HttpStatus> supported = EnumSet.of(
                HttpStatus.OK,
                HttpStatus.BAD_REQUEST,
                HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT,
                HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.SERVICE_UNAVAILABLE
        );

        for (ErrorCode errorCode : ErrorCode.values()) {
            assertTrue(supported.contains(errorCode.getHttpStatus()), errorCode.name());
        }
    }

    @Test
    void handlerKeepsEnvelopeAndUsesErrorHttpStatus() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertStatus(handler, ErrorCode.PARAMS_ERROR, HttpStatus.BAD_REQUEST);
        assertStatus(handler, ErrorCode.NOT_LOGIN_ERROR, HttpStatus.UNAUTHORIZED);
        assertStatus(handler, ErrorCode.FORBIDDEN_ERROR, HttpStatus.FORBIDDEN);
        assertStatus(handler, ErrorCode.ACCOUNT_ALREADY_EXISTS, HttpStatus.CONFLICT);
        assertStatus(handler, ErrorCode.RATE_LIMIT_ERROR, HttpStatus.TOO_MANY_REQUESTS);
        assertStatus(handler, ErrorCode.AI_SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void assertStatus(GlobalExceptionHandler handler, ErrorCode code, HttpStatus status) {
        ResponseEntity<BaseResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(code));
        assertEquals(status, response.getStatusCode());
        assertEquals(code.getCode(), response.getBody().getCode());
        assertEquals(code.getMessage(), response.getBody().getMessage());
    }
}
