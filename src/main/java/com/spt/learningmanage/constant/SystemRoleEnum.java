package com.spt.learningmanage.constant;

/**
 * Compatibility name used by the architecture documents. New code may use
 * {@link SystemRole}; both expose the same canonical values.
 */
public enum SystemRoleEnum {
    USER,
    SYSTEM_ADMIN;

    public static SystemRoleEnum fromValue(String value) {
        SystemRole role = SystemRole.fromValue(value);
        return role == null ? null : valueOf(role.name());
    }

    public static String canonicalize(String value) {
        SystemRoleEnum role = fromValue(value);
        return role == null ? null : role.name();
    }
}
