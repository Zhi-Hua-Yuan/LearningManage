package com.spt.learningmanage.ai.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAiContentSanitizerTest {

    private DefaultAiContentSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.getLogging().setMaxBodyChars(40);
        properties.getLogging().setMaxErrorChars(20);
        sanitizer = new DefaultAiContentSanitizer(new ObjectMapper(), properties);
    }

    @Test
    void shouldRedactNestedJsonAndRemainIdempotent() {
        String source = "{\"profile\":{\"password\":\"p@ss\"},\"title\":\"token 学习笔记\"}";

        AiSanitizedContent first = sanitizer.sanitizeForProvider(source);
        AiSanitizedContent second = sanitizer.sanitizeForProvider(first.value());

        assertEquals(AiSanitizationStatus.REDACTED, first.status());
        assertFalse(first.value().contains("p@ss"));
        assertTrue(first.value().contains("token 学习笔记"));
        assertEquals(first.value(), second.value());
    }

    @Test
    void shouldRedactJwtAuthorizationAndDatabasePassword() {
        String source = "Authorization: Bearer abc.def.ghi "
                + "jwt=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijklmnopqrstuvwxyz "
                + "mysql://user:plain-secret@localhost/db";

        AiSanitizedContent result = sanitizer.sanitizeForProvider(source);

        assertEquals(AiSanitizationStatus.REDACTED, result.status());
        assertFalse(result.value().contains("plain-secret"));
        assertFalse(result.value().contains("eyJhbGci"));
        assertFalse(result.value().contains("Bearer abc"));
    }

    @Test
    void shouldHashFullSanitizedValueBeforeTruncation() {
        AiSanitizedContent shortResult = sanitizer.sanitizeForProvider("password=secret-value-and-more");
        AiSanitizedContent storedResult = sanitizer.sanitizeForLog("password=secret-value-and-more", true);

        assertTrue(storedResult.truncated());
        assertEquals(20, storedResult.value().length());
        assertEquals(shortResult.sha256(), storedResult.sha256());
        assertNotEquals(storedResult.value(), shortResult.value());
    }
}
