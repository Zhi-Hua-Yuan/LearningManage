package com.spt.learningmanage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Validates the transport adapter selector before any adapter is injected.
 * Keeping this check at startup prevents a typo from producing an opaque
 * "no qualifying AiChatAdapter" dependency error later in the context.
 */
@Component
public class AiChatAdapterConfigurationValidator {

    private final AiProperties properties;

    public AiChatAdapterConfigurationValidator(AiProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        String adapter = properties.getChat() == null ? null : properties.getChat().getAdapter();
        if (!"legacy".equals(adapter) && !"spring-ai".equals(adapter)) {
            throw new IllegalStateException(
                    "ai.chat.adapter must be one of [legacy, spring-ai], but was: " + adapter);
        }
    }
}
