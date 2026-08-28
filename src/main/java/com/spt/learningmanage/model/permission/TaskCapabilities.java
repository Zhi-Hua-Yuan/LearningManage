package com.spt.learningmanage.model.permission;

/**
 * Trusted task capabilities calculated by PermissionService.
 *
 * <p>The value is a presentation hint only. Every mutating operation must
 * still perform a fresh server-side permission check.</p>
 */
public record TaskCapabilities(
        boolean canEditContent,
        boolean canChangeStatus,
        boolean canReorganize,
        boolean canAssign,
        boolean canDelete
) {
}
