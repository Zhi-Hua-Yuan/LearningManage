package com.spt.learningmanage.exception;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.ConstraintViolationException;
import com.spt.learningmanage.observability.AiMetricsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private AiMetricsRecorder aiMetricsRecorder;

    @Autowired(required = false)
    public void setAiMetricsRecorder(AiMetricsRecorder aiMetricsRecorder) {
        this.aiMetricsRecorder = aiMetricsRecorder;
    }

    @ExceptionHandler(BusinessException.class)
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "请求参数或 Schema 错误"),
            @ApiResponse(responseCode = "401", description = "未登录或登录已失效"),
            @ApiResponse(responseCode = "403", description = "权限不足"),
            @ApiResponse(responseCode = "404", description = "资源不存在"),
            @ApiResponse(responseCode = "409", description = "状态冲突"),
            @ApiResponse(responseCode = "429", description = "限流、额度或并发超限"),
            @ApiResponse(responseCode = "500", description = "服务端操作失败"),
            @ApiResponse(responseCode = "503", description = "依赖或功能暂不可用")
    })
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException ex,
                                                                        HttpServletRequest request) {
        String outcome = classifyAiOutcome(ex.getErrorCode());
        if (outcome != null && aiMetricsRecorder != null && isAiRequest(request)) {
            aiMetricsRecorder.recordSceneOutcome(sceneFromRequest(request), outcome);
        }
        log.warn("业务异常 code={} outcome={} message={}", ex.getErrorCode(), outcome, ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ResultUtils.error(ex.getErrorCode(), ex.getMessage()));
    }

    /** Compatibility overload used by unit callers that do not have a servlet request. */
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException ex) {
        return handleBusinessException(ex, null);
    }

    private boolean isAiRequest(HttpServletRequest request) {
        return request != null && request.getRequestURI() != null
                && request.getRequestURI().contains("/ai/");
    }

    private String sceneFromRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("today-order")) {
            return "today-order";
        }
        if (uri.contains("daily-review")) {
            return "daily-review-rename";
        }
        if (uri.contains("list/replan")) {
            return "list-replan";
        }
        if (uri.contains("polish")) {
            return "weekly-polish";
        }
        if (uri.contains("breakdown")) {
            return "task-breakdown";
        }
        return "ai-other";
    }

    private String classifyAiOutcome(ErrorCode errorCode) {
        if (errorCode == null) {
            return null;
        }
        return switch (errorCode) {
            case NOT_LOGIN_ERROR, NO_AUTH_ERROR, FORBIDDEN_ERROR, REPORT_NO_ACCESS,
                    TOOL_NOT_ALLOWED -> "AUTHORIZATION";
            case RESOURCE_STATE_CONFLICT, AI_DRAFT_NOT_CONFIRMABLE, AI_DRAFT_EXPIRED,
                    AI_DRAFT_CONFLICT, RAG_RESULT_INVALIDATED, RAG_RESULT_EXPIRED,
                    RAG_SOURCE_CHANGED, AGENT_RUN_NOT_FINISHED, AGENT_RUN_ALREADY_FINISHED,
                    AGENT_CANCELED, AGENT_DATA_CHANGED, AGENT_REPORT_STALE,
                    REPORT_ALREADY_DELETED -> "RESOURCE_STATE_CONFLICT";
            default -> null;
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(ErrorCode.PARAMS_ERROR.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleConstraintViolation(ConstraintViolationException ex) {
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, ex.getMessage());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAMS_ERROR.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "参数类型错误");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求体格式错误");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<Void> handleException(Exception ex) {
        log.error("系统内部异常: ", ex);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
    }
}
