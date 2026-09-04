package com.spt.learningmanage.service.impl.ai.scene;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.ai.pipeline.AiRawExecutionCommand;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.service.ai.scene.AiChatCompatibilityService;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.support.AiSceneSupport;
import com.spt.learningmanage.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiChatCompatibilityServiceImpl extends AiSceneSupport implements AiChatCompatibilityService {

    private static final Logger log = LoggerFactory.getLogger(AiChatCompatibilityServiceImpl.class);

    private final AiInvocationPipeline aiInvocationPipeline;
    private final AiModelSelector modelSelector;

    public AiChatCompatibilityServiceImpl(AiInvocationPipeline aiInvocationPipeline,
                                          AiModelSelector modelSelector) {
        this.aiInvocationPipeline = aiInvocationPipeline;
        this.modelSelector = modelSelector;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (StrUtil.isBlank(systemPrompt) || StrUtil.isBlank(userPrompt)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词不能为空");
        }
        try {
            return aiInvocationPipeline.executeRaw(new AiRawExecutionCommand(
                    UserHolder.get(), modelSelector.defaultModel(), systemPrompt, userPrompt,
                    "AI 通用对话结果格式异常", null
            ), rawContent -> rawContent).data();
        } catch (AiInvocationException exception) {
            log.warn("AI 通用对话调用失败: type={}, model={}",
                    exception.getFailureType(), exception.getModelName(), exception);
            throw toBusinessException(exception);
        }
    }
}
