package com.spt.learningmanage.service.impl.ai.support;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import org.springframework.stereotype.Service;

@Service
public class AiModelSelectorImpl implements AiModelSelector {

    private final AiProperties aiProperties;

    public AiModelSelectorImpl(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public String defaultModel() {
        return resolve(aiProperties.getModel());
    }

    @Override
    public String breakdownModel() {
        return resolve(aiProperties.getBreakdownModel());
    }

    @Override
    public String polishModel() {
        return resolve(aiProperties.getPolishModel());
    }

    private String resolve(String preferredModel) {
        String model = StrUtil.isNotBlank(preferredModel)
                ? preferredModel.trim()
                : StrUtil.trim(aiProperties.getModel());
        if (StrUtil.isBlank(model)) {
            throw new BusinessException(ErrorCode.AI_CONFIG_ERROR, "AI 服务配置异常，请联系管理员");
        }
        return model;
    }
}
