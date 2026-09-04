package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiGovernanceConfigurationValidatorTest {

    @Test
    void shouldAcceptDefaultsAndEmptyPricingCatalog() {
        AiProperties properties = configuredProperties();
        properties.getPricing().getModels().put("qwen-plus", new AiProperties.ModelPrice());

        assertDoesNotThrow(() -> new AiGovernanceConfigurationValidator(properties).validate());
    }

    @Test
    void shouldRejectMissingProviderConfigurationWhenChatIsEnabled() {
        assertThrows(IllegalStateException.class,
                () -> new AiGovernanceConfigurationValidator(new AiProperties()).validate());
    }

    @Test
    void shouldAllowMissingProviderConfigurationWhenChatIsDisabled() {
        AiProperties properties = new AiProperties();
        properties.getChat().setEnabled(false);

        assertDoesNotThrow(() -> new AiGovernanceConfigurationValidator(properties).validate());
    }

    @Test
    void shouldRejectInvalidTimeoutOrCurrency() {
        AiProperties properties = configuredProperties();
        properties.setReadTimeoutMs(100);

        assertThrows(IllegalStateException.class,
                () -> new AiGovernanceConfigurationValidator(properties).validate());

        AiProperties invalidCurrency = configuredProperties();
        invalidCurrency.getPricing().setCurrency("not-a-currency");
        assertThrows(IllegalStateException.class,
                () -> new AiGovernanceConfigurationValidator(invalidCurrency).validate());
    }

    @Test
    void shouldRejectPartialOrNegativePrice() {
        AiProperties properties = configuredProperties();
        properties.getPricing().setVersion("v1");
        AiProperties.ModelPrice price = new AiProperties.ModelPrice();
        price.setInputPerMillion(BigDecimal.ONE.negate());
        properties.getPricing().getModels().put("qwen-plus", price);

        assertThrows(IllegalStateException.class,
                () -> new AiGovernanceConfigurationValidator(properties).validate());
    }

    private AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-provider-key");
        properties.setBaseUrl("https://provider.example.test/v1");
        properties.setModel("qwen-test");
        return properties;
    }
}
