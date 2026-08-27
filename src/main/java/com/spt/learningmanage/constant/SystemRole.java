package com.spt.learningmanage.constant;

import java.util.Locale;

/**
 * System-level roles. Team roles are represented separately by {@link TeamRoleEnum}.
 */
public enum SystemRole {
    USER,
    SYSTEM_ADMIN;

    public static SystemRole fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(normalized)) {
            return SYSTEM_ADMIN;
        }
        if ("SYSTEM_ADMIN".equals(normalized)) {
            return SYSTEM_ADMIN;
        }
        if ("USER".equals(normalized)) {
            return USER;
        }
        return null;
    }

    public static String canonicalize(String value) {
        SystemRole role = fromValue(value);
        return role == null ? null : role.name();
    }
}
