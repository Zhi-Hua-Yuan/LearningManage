package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlywayAdminContractTest {

    @Test
    void baselineRequiresExplicitTargetAndAuthorization() {
        Map<String, String> environment = new HashMap<>();
        environment.put("FLYWAY_BASELINE_AUTHORIZED", "true");
        environment.put("DB_NAME", "learning_manage_flyway_rehearsal");
        environment.put("FLYWAY_EXPECTED_DB_NAME", "learning_manage_flyway_rehearsal");
        environment.put("FLYWAY_BASELINE_VERSION", "1");

        assertDoesNotThrow(() -> FlywayAdmin.requireBaselineAuthorization(environment));
    }

    @Test
    void baselineRejectsMissingAuthorization() {
        Map<String, String> environment = new HashMap<>();
        environment.put("DB_NAME", "learning_manage");
        environment.put("FLYWAY_EXPECTED_DB_NAME", "learning_manage");
        environment.put("FLYWAY_BASELINE_VERSION", "1");

        assertThrows(IllegalStateException.class, () -> FlywayAdmin.requireBaselineAuthorization(environment));
    }

    @Test
    void adminActionDoesNotExposeUnsupportedOperations() {
        assertDoesNotThrow(() -> FlywayAdmin.parseAction(new String[]{"info"}));
        assertThrows(IllegalArgumentException.class, () -> FlywayAdmin.parseAction(new String[]{"clean"}));
        assertThrows(IllegalArgumentException.class, () -> FlywayAdmin.parseAction(new String[]{"repair"}));
    }

    @Test
    void optionalTargetVersionAcceptsOnlyPositiveIntegerVersions() {
        assertNull(FlywayAdmin.optionalTargetVersion(Map.of()));
        assertNull(FlywayAdmin.optionalTargetVersion(Map.of("FLYWAY_TARGET_VERSION", " ")));
        assertEquals("2", FlywayAdmin.optionalTargetVersion(Map.of("FLYWAY_TARGET_VERSION", "2")));
        assertThrows(IllegalArgumentException.class,
                () -> FlywayAdmin.optionalTargetVersion(Map.of("FLYWAY_TARGET_VERSION", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> FlywayAdmin.optionalTargetVersion(Map.of("FLYWAY_TARGET_VERSION", "latest")));
    }
}
