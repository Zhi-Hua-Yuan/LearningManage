package com.spt.learningmanage.exception;

/**
 * 已登录用户没有执行指定资源动作的权限。
 *
 * <p>权限拒绝统一使用 {@link ErrorCode#FORBIDDEN_ERROR}。异常不接受资源
 * ID、资源所有者或其他动态消息，避免把资源存在性和权限事实泄露给客户端。</p>
 */
public final class PermissionDeniedException extends BusinessException {

    private static final String DEFAULT_MESSAGE = "无权限执行该操作";

    public PermissionDeniedException() {
        super(ErrorCode.FORBIDDEN_ERROR, DEFAULT_MESSAGE);
    }
}
