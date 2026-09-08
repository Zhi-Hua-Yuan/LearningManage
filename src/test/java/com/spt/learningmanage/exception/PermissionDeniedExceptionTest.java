package com.spt.learningmanage.exception;

import com.spt.learningmanage.common.BaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PermissionDeniedExceptionTest {

    @Test
    void shouldUseStableForbiddenErrorAndGenericMessage() {
        PermissionDeniedException exception = new PermissionDeniedException();

        assertInstanceOf(BusinessException.class, exception);
        assertEquals(ErrorCode.FORBIDDEN_ERROR, exception.getErrorCode());
        assertEquals(40300, exception.getErrorCode().getCode());
        assertEquals("无权限执行该操作", exception.getMessage());
    }

    @Test
    void globalHandlerShouldKeepExistingBaseResponseContract() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<BaseResponse<Void>> entity = handler.handleBusinessException(
                new PermissionDeniedException()
        );
        BaseResponse<Void> response = entity.getBody();

        assertEquals(403, entity.getStatusCode().value());
        assertEquals(40300, response.getCode());
        assertEquals("无权限执行该操作", response.getMessage());
    }
}
