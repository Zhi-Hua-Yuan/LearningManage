package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.constant.AiCallFailureTypeEnum;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.EmbeddingClient;
import com.spt.learningmanage.service.KnowledgeEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingServiceImpl.class);
    private static final String SCENE = "knowledge-index";
    private static final String PROMPT_CODE = "embedding-document";

    private final EmbeddingClient client;
    private final AiCallLogService callLogService;
    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;

    public KnowledgeEmbeddingServiceImpl(EmbeddingClient client,
                                         AiCallLogService callLogService,
                                         EmbeddingProperties properties,
                                         ObjectMapper objectMapper) {
        this.client = client;
        this.callLogService = callLogService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public EmbeddingBatchResult embedDocuments(List<String> texts, EmbeddingCallContext context) {
        Long logId = createLog(texts, context);
        long startedAt = System.currentTimeMillis();
        try {
            EmbeddingBatchResult result = client.embedDocuments(texts, context);
            completeSuccess(logId, result, System.currentTimeMillis() - startedAt, context);
            return result;
        } catch (KnowledgeIndexException exception) {
            completeFailure(logId, exception, System.currentTimeMillis() - startedAt, context);
            throw exception;
        }
    }

    private Long createLog(List<String> texts, EmbeddingCallContext context) {
        if (context == null || context.ownerUserId() == null) {
            return null;
        }
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("itemCount", texts.size());
            metadata.put("characterCount", texts.stream().mapToInt(String::length).sum());
            metadata.put("contentHashes", context.contentHashes());
            metadata.put("dimension", properties.getDimension());
            return callLogService.createRunningLog(new AiCallLogCreateCommand(
                    context.ownerUserId(), SCENE, properties.getModel(), PROMPT_CODE,
                    null, 1, "runtime", objectMapper.writeValueAsString(metadata), 0,
                    context.traceId()
            ));
        } catch (Exception exception) {
            log.warn("embedding call log creation failed: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private void completeSuccess(Long logId,
                                 EmbeddingBatchResult result,
                                 long duration,
                                 EmbeddingCallContext context) {
        if (logId == null) {
            return;
        }
        try {
            String response = objectMapper.writeValueAsString(Map.of(
                    "vectorCount", result.vectors().size(),
                    "dimension", properties.getDimension()
            ));
            callLogService.complete(new AiCallLogCompletionCommand(
                    logId, AiCallLogStatusEnum.SUCCESS, response, null, duration,
                    properties.getModel(), result.actualModel(), 0, null,
                    usage(result), result.providerRequestId(), false, null,
                    context.traceId(), null, false, null
            ));
        } catch (Exception exception) {
            log.warn("embedding call log completion failed: logId={}", logId);
        }
    }

    private void completeFailure(Long logId,
                                 KnowledgeIndexException exception,
                                 long duration,
                                 EmbeddingCallContext context) {
        if (logId == null) {
            return;
        }
        try {
            AiCallFailureTypeEnum failureType = mapFailure(exception.getFailureType());
            AiCallLogStatusEnum status = failureType == AiCallFailureTypeEnum.TIMEOUT
                    ? AiCallLogStatusEnum.TIMEOUT
                    : failureType == AiCallFailureTypeEnum.PROTOCOL
                    ? AiCallLogStatusEnum.PARSE_FAILED : AiCallLogStatusEnum.FAILED;
            callLogService.complete(new AiCallLogCompletionCommand(
                    logId, status, null, exception.getSafeMessage(), duration,
                    properties.getModel(), properties.getModel(), 0, null,
                    null, null, false, null, context.traceId(), failureType,
                    false, null
            ));
        } catch (Exception completionFailure) {
            log.warn("embedding failure log completion failed: logId={}", logId);
        }
    }

    private AiUsage usage(EmbeddingBatchResult result) {
        return new AiUsage(integer(result.promptTokens()), 0, integer(result.totalTokens()));
    }

    private Integer integer(Long value) {
        if (value == null) {
            return null;
        }
        return Math.toIntExact(Math.min(value, Integer.MAX_VALUE));
    }

    private AiCallFailureTypeEnum mapFailure(KnowledgeFailureTypeEnum type) {
        return switch (type) {
            case CONFIG -> AiCallFailureTypeEnum.CONFIG;
            case AUTH -> AiCallFailureTypeEnum.AUTH;
            case RATE_LIMIT -> AiCallFailureTypeEnum.RATE_LIMIT;
            case NETWORK, VECTOR_STORE -> AiCallFailureTypeEnum.NETWORK;
            case TIMEOUT -> AiCallFailureTypeEnum.TIMEOUT;
            case EMBEDDING_PROTOCOL, DIMENSION_MISMATCH -> AiCallFailureTypeEnum.PROTOCOL;
            case STALE_SOURCE -> AiCallFailureTypeEnum.BUSINESS_VALIDATION;
            case INTERNAL -> AiCallFailureTypeEnum.INTERNAL;
        };
    }
}
