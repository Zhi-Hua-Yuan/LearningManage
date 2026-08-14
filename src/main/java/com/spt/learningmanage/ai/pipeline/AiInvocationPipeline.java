package com.spt.learningmanage.ai.pipeline;

import cn.hutool.json.JSONUtil;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 统一编排提示词解析、模型调用、响应处理和调用日志生命周期。
 *
 * <p>业务数据准备、场景降级和正式数据写入不属于本管线职责。</p>
 */
@Component
public class AiInvocationPipeline {

    private static final Logger log = LoggerFactory.getLogger(AiInvocationPipeline.class);

    private final PromptTemplateResolver promptTemplateResolver;
    private final AiModelClient aiModelClient;
    private final AiCallLogService aiCallLogService;

    public AiInvocationPipeline(PromptTemplateResolver promptTemplateResolver,
                                AiModelClient aiModelClient,
                                AiCallLogService aiCallLogService) {
        this.promptTemplateResolver = promptTemplateResolver;
        this.aiModelClient = aiModelClient;
        this.aiCallLogService = aiCallLogService;
    }

    public <T> AiExecutionResult<T> execute(AiExecutionCommand command,
                                            AiResponseProcessor<T> responseProcessor) {
        if (command == null) {
            throw new IllegalArgumentException("AI 执行命令不能为空");
        }
        if (responseProcessor == null) {
            throw new IllegalArgumentException("AI 响应处理器不能为空");
        }

        AiPromptTemplate promptTemplate = promptTemplateResolver.resolve(command.promptCode());
        Long callLogId = createCallLogSafely(command, promptTemplate);
        long startTime = System.currentTimeMillis();

        AiInvocationResult invocationResult = invokeModel(
                command,
                promptTemplate,
                callLogId,
                startTime
        );

        try {
            T data = responseProcessor.process(invocationResult.content());
            if (data == null) {
                throw new IllegalStateException("AI 响应处理结果不能为空");
            }

            long costTimeMs = elapsedSince(startTime);
            AiExecutionResult<T> executionResult = new AiExecutionResult<>(
                    data,
                    callLogId,
                    invocationResult.actualModel(),
                    invocationResult.retryCount(),
                    costTimeMs
            );
            markSuccessSafely(callLogId, invocationResult.content(), costTimeMs);
            return executionResult;
        } catch (Exception exception) {
            long costTimeMs = elapsedSince(startTime);
            markParseFailedSafely(
                    callLogId,
                    invocationResult.content(),
                    command.parseFailureMessage(),
                    costTimeMs
            );
            throw new AiResponseProcessingException(command.parseFailureMessage(), exception);
        }
    }

    private AiInvocationResult invokeModel(AiExecutionCommand command,
                                           AiPromptTemplate promptTemplate,
                                           Long callLogId,
                                           long startTime) {
        try {
            AiInvocationResult invocationResult = aiModelClient.invoke(
                    command.modelName(),
                    promptTemplate.systemPrompt(),
                    command.userPrompt()
            );
            updateExecutionMetadataSafely(
                    callLogId,
                    invocationResult.actualModel(),
                    invocationResult.retryCount()
            );
            return invocationResult;
        } catch (AiInvocationException exception) {
            markInvocationFailedSafely(callLogId, exception, elapsedSince(startTime));
            throw exception;
        } catch (Exception exception) {
            AiInvocationException wrapped = new AiInvocationException(
                    AiFailureTypeEnum.INTERNAL_ERROR,
                    command.modelName(),
                    0,
                    "AI 服务暂时不可用，请稍后重试",
                    "AI 调用发生未分类异常：model=" + command.modelName(),
                    exception
            );
            markInvocationFailedSafely(callLogId, wrapped, elapsedSince(startTime));
            throw wrapped;
        }
    }

    private Long createCallLogSafely(AiExecutionCommand command,
                                     AiPromptTemplate promptTemplate) {
        try {
            return aiCallLogService.createRunningLog(new AiCallLogCreateCommand(
                    command.userId(),
                    promptTemplate.scene(),
                    command.modelName(),
                    promptTemplate.code(),
                    promptTemplate.templateId(),
                    promptTemplate.version(),
                    promptTemplate.source().getCode(),
                    buildRequestText(promptTemplate.systemPrompt(), command.userPrompt()),
                    0
            ));
        } catch (Exception exception) {
            log.warn("AI 调用日志创建失败：scene={}, model={}",
                    promptTemplate.scene(), command.modelName(), exception);
            return null;
        }
    }

    private void markInvocationFailedSafely(Long callLogId,
                                            AiInvocationException exception,
                                            long costTimeMs) {
        updateExecutionMetadataSafely(
                callLogId,
                exception.getModelName(),
                exception.getRetryCount()
        );
        if (exception.getFailureType() == AiFailureTypeEnum.TIMEOUT) {
            markTimeoutSafely(callLogId, exception.getSafeMessage(), costTimeMs);
            return;
        }
        if (exception.getFailureType() == AiFailureTypeEnum.INVALID_RESPONSE) {
            markParseFailedSafely(callLogId, null, exception.getSafeMessage(), costTimeMs);
            return;
        }
        markFailedSafely(callLogId, exception.getSafeMessage(), costTimeMs);
    }

    private void updateExecutionMetadataSafely(Long callLogId,
                                               String actualModel,
                                               Integer retryCount) {
        if (callLogId == null) {
            return;
        }
        try {
            aiCallLogService.updateExecutionMetadata(callLogId, actualModel, retryCount);
        } catch (Exception exception) {
            log.warn("AI 调用日志执行元数据更新失败：logId={}", callLogId, exception);
        }
    }

    private void markSuccessSafely(Long callLogId, String responseText, long costTimeMs) {
        if (callLogId == null) {
            return;
        }
        try {
            aiCallLogService.markSuccess(callLogId, responseText, costTimeMs);
        } catch (Exception exception) {
            log.warn("AI 调用日志成功状态更新失败：logId={}", callLogId, exception);
        }
    }

    private void markFailedSafely(Long callLogId, String errorMessage, long costTimeMs) {
        if (callLogId == null) {
            return;
        }
        try {
            aiCallLogService.markFailed(callLogId, errorMessage, costTimeMs);
        } catch (Exception exception) {
            log.warn("AI 调用日志失败状态更新失败：logId={}", callLogId, exception);
        }
    }

    private void markTimeoutSafely(Long callLogId, String errorMessage, long costTimeMs) {
        if (callLogId == null) {
            return;
        }
        try {
            aiCallLogService.markTimeout(callLogId, errorMessage, costTimeMs);
        } catch (Exception exception) {
            log.warn("AI 调用日志超时状态更新失败：logId={}", callLogId, exception);
        }
    }

    private void markParseFailedSafely(Long callLogId,
                                       String responseText,
                                       String errorMessage,
                                       long costTimeMs) {
        if (callLogId == null) {
            return;
        }
        try {
            aiCallLogService.markParseFailed(
                    callLogId,
                    responseText,
                    errorMessage,
                    costTimeMs
            );
        } catch (Exception exception) {
            log.warn("AI 调用日志解析失败状态更新失败：logId={}", callLogId, exception);
        }
    }

    private String buildRequestText(String systemPrompt, String userPrompt) {
        return JSONUtil.createObj()
                .set("systemPrompt", systemPrompt)
                .set("userPrompt", userPrompt)
                .toString();
    }

    private long elapsedSince(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
