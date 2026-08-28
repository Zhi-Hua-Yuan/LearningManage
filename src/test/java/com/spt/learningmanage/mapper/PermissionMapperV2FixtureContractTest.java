package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionMapperV2FixtureContractTest {

    private static final Path FIXTURE = Path.of(
            "src/test/resources/db/stage1/permission_mapper_v2_seed.sql"
    );

    @Test
    void fixtureShouldBeDeterministicDmlOnlyDataWithoutProductionSecrets() throws Exception {
        String sql = Files.readString(FIXTURE, StandardCharsets.UTF_8);
        String lower = sql.toLowerCase();

        assertFalse(Pattern.compile("(?m)^\\s*(create|alter|drop|delete)\\b")
                .matcher(sql)
                .find());
        assertTrue(lower.contains("insert into `user`"));
        assertTrue(lower.contains("insert into `team`"));
        assertTrue(lower.contains("insert into `team_member`"));
        assertTrue(lower.contains("insert into `project`"));
        assertTrue(lower.contains("insert into `task`"));
        assertTrue(lower.contains("insert into `weekly_review`"));
        assertTrue(lower.contains("not-a-real-password-hash"));
        assertFalse(lower.contains("real_password"));
        assertFalse(lower.contains("authorization:"));
        assertFalse(lower.contains("bearer "));
    }
}
