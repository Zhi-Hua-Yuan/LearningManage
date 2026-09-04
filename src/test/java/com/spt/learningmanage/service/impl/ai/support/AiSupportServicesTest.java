package com.spt.learningmanage.service.impl.ai.support;

import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSupportServicesTest {

    @Test
    void modelSelectorPreservesSceneOverrideAndDefaultFallback() {
        AiProperties properties = mock(AiProperties.class);
        when(properties.getModel()).thenReturn(" default-model ");
        when(properties.getBreakdownModel()).thenReturn(" scene-model ");
        AiModelSelectorImpl selector = new AiModelSelectorImpl(properties);

        assertEquals("default-model", selector.defaultModel());
        assertEquals("scene-model", selector.breakdownModel());
    }

    @Test
    void modelSelectorRejectsMissingModelConfiguration() {
        AiProperties properties = mock(AiProperties.class);
        AiModelSelectorImpl selector = new AiModelSelectorImpl(properties);

        assertThrows(BusinessException.class, selector::polishModel);
    }

    @Test
    void jsonSanitizerExtractsFencedArrayAndObject() {
        AiJsonResponseSanitizerImpl sanitizer = new AiJsonResponseSanitizerImpl();

        assertEquals("[{\"id\":1}]", sanitizer.sanitizeArray("text```json\n[{\"id\":1}]\n```tail"));
        assertEquals("{\"review\":\"ok\"}", sanitizer.sanitizeObject("```JSON\n{\"review\":\"ok\"}\n```"));
    }

    @Test
    void jsonSanitizerRejectsBlankResponse() {
        AiJsonResponseSanitizerImpl sanitizer = new AiJsonResponseSanitizerImpl();

        assertThrows(BusinessException.class, () -> sanitizer.sanitizeObject("  "));
    }
}
