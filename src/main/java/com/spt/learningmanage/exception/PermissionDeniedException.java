package com.spt.learningmanage.exception;

/**
 * Stable exception for authorization failures. It intentionally does not expose
 * resource ownership or team membership details.
 */
public class PermissionDeniedException extends BusinessException {

    public PermissionDeniedException() {
        super(ErrorCode.FORBIDDEN_ERROR, "无权限执行该操作");
    }
}
