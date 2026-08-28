package com.spt.learningmanage.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemRoleEnumTest {

    @Test
    void fromValueShouldResolveUser() {
        assertEquals(SystemRoleEnum.USER, SystemRoleEnum.fromValue("USER"));
    }

    @Test
    void fromValueShouldResolveSystemAdmin() {
        assertEquals(SystemRoleEnum.SYSTEM_ADMIN, SystemRoleEnum.fromValue("SYSTEM_ADMIN"));
    }

    @Test
    void fromValueShouldRejectLegacyLowercaseUser() {
        assertNull(SystemRoleEnum.fromValue("user"));
    }

    @Test
    void fromValueShouldRejectLegacyLowercaseAdmin() {
        assertNull(SystemRoleEnum.fromValue("admin"));
    }

    @Test
    void fromValueShouldRejectWhitespace() {
        assertNull(SystemRoleEnum.fromValue(" USER "));
    }

    @Test
    void fromValueShouldReturnNullForNull() {
        assertNull(SystemRoleEnum.fromValue(null));
    }

    @Test
    void isValidValueShouldAcceptOnlyCanonicalValues() {
        assertTrue(SystemRoleEnum.isValidValue("USER"));
        assertTrue(SystemRoleEnum.isValidValue("SYSTEM_ADMIN"));
        assertFalse(SystemRoleEnum.isValidValue("user"));
        assertFalse(SystemRoleEnum.isValidValue(""));
    }

    @Test
    void isSystemAdminShouldOnlyAcceptCanonicalAdmin() {
        assertTrue(SystemRoleEnum.isSystemAdmin("SYSTEM_ADMIN"));
        assertFalse(SystemRoleEnum.isSystemAdmin("admin"));
        assertFalse(SystemRoleEnum.isSystemAdmin("USER"));
        assertFalse(SystemRoleEnum.isSystemAdmin(null));
    }
}
