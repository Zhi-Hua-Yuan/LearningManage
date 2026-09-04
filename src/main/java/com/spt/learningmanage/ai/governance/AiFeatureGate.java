package com.spt.learningmanage.ai.governance;

import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import org.springframework.stereotype.Component;

@Component
public class AiFeatureGate {

    private final AiProperties aiProperties;

    public AiFeatureGate(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public void requireChatEnabled(String model) {
        if (!isChatEnabled()) {
            throw new AiInvocationException(
                    AiFailureTypeEnum.FEATURE_DISABLED,
                    model,
                    0,
                    "AI 生成功能已关闭",
                    "AI chat feature is disabled",
                    null
            );
        }
    }

    public boolean isChatEnabled() {
        return aiProperties.getChat().isEnabled();
    }
}
