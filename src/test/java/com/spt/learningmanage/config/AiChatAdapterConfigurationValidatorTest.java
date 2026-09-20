package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiChatAdapterConfigurationValidatorTest {

    @Test
    void acceptsLegacyAndSpringAiSelectors() {
        AiProperties properties = new AiProperties();
        AiChatAdapterConfigurationValidator validator =
                new AiChatAdapterConfigurationValidator(properties);

        assertDoesNotThrow(validator::validate);
        properties.getChat().setAdapter("spring-ai");
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void rejectsUnknownSelector() {
        AiProperties properties = new AiProperties();
        properties.getChat().setAdapter("typo");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new AiChatAdapterConfigurationValidator(properties).validate());

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("ai.chat.adapter"));
    }
}
