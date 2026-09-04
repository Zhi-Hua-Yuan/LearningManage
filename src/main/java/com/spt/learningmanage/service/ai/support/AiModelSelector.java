package com.spt.learningmanage.service.ai.support;

public interface AiModelSelector {
    String defaultModel();

    String breakdownModel();

    String polishModel();
}
