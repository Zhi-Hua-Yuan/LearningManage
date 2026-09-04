package com.spt.learningmanage.service.impl.ai.support;

import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiSceneSupportTest {

    private final ExposedAiSceneSupport support = new ExposedAiSceneSupport();

    @Test
    void shouldMapGovernanceFailuresToStablePublicErrors() {
        Map<AiFailureTypeEnum, ErrorCode> expected = Map.of(
                AiFailureTypeEnum.FEATURE_DISABLED, ErrorCode.AI_DISABLED,
                AiFailureTypeEnum.CONCURRENCY_LIMIT, ErrorCode.AI_CONCURRENCY_LIMIT,
                AiFailureTypeEnum.CONTENT_BLOCKED, ErrorCode.AI_CONTENT_BLOCKED,
                AiFailureTypeEnum.CIRCUIT_OPEN, ErrorCode.AI_SERVICE_UNAVAILABLE
        );

        expected.forEach((failureType, errorCode) -> {
            AiInvocationException source = new AiInvocationException(
                    failureType, "qwen-test", 0, "safe", "internal", null);
            BusinessException mapped = support.map(source);
            assertEquals(errorCode, mapped.getErrorCode());
            assertEquals("safe", mapped.getMessage());
        });
    }

    private static final class ExposedAiSceneSupport extends AiSceneSupport {
        private BusinessException map(AiInvocationException exception) {
            return toBusinessException(exception);
        }
    }
}
