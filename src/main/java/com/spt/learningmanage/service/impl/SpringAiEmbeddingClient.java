package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.ai.governance.AiSanitizationStatus;
import com.spt.learningmanage.client.knowledge.SpringAiEmbeddingModel;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.service.EmbeddingClient;
import com.spt.learningmanage.service.knowledge.KnowledgeDependencyType;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import java.util.ArrayList;
import java.util.List;

/** EmbeddingClient compatibility facade backed by application-owned Spring AI models. */
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final SpringAiEmbeddingModel documentModel;
    private final SpringAiEmbeddingModel queryModel;
    private final AiContentSanitizer contentSanitizer;
    private final KnowledgeResilientCallExecutor resilientCallExecutor;

    public SpringAiEmbeddingClient(EmbeddingProperties properties,
                                   ObjectMapper objectMapper,
                                   SpringAiEmbeddingModel documentModel,
                                   SpringAiEmbeddingModel queryModel,
                                   AiContentSanitizer contentSanitizer,
                                   KnowledgeResilientCallExecutor resilientCallExecutor) {
        this.properties = properties;
        this.documentModel = documentModel;
        this.queryModel = queryModel;
        this.contentSanitizer = contentSanitizer;
        this.resilientCallExecutor = resilientCallExecutor;
    }

    @Override
    public EmbeddingBatchResult embedDocuments(List<String> texts, EmbeddingCallContext context) {
        validateInput(texts);
        List<String> sanitized = texts.stream().map(this::sanitize).toList();
        EmbeddingResponse response = execute(() -> documentModel.call(new EmbeddingRequest(
                sanitized,
                OpenAiEmbeddingOptions.builder().model(properties.getModel())
                        .dimensions(properties.getDimension()).encodingFormat("float").build())));
        return map(response, texts.size());
    }

    @Override
    public EmbeddingBatchResult embedQuery(String text, EmbeddingCallContext context) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding 查询内容不能为空");
        }
        String sanitized = sanitize(text);
        EmbeddingResponse response = execute(() -> queryModel.call(new EmbeddingRequest(
                List.of(sanitized),
                OpenAiEmbeddingOptions.builder().model(properties.getModel())
                        .dimensions(properties.getDimension()).build())));
        return map(response, 1);
    }

    private EmbeddingResponse execute(java.util.function.Supplier<EmbeddingResponse> action) {
        try {
            return resilientCallExecutor.execute(KnowledgeDependencyType.EMBEDDING, action);
        } catch (KnowledgeIndexException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.EMBEDDING_PROTOCOL, false,
                    "Embedding 返回格式异常", "Spring AI Embedding 执行失败", exception);
        }
    }

    private EmbeddingBatchResult map(EmbeddingResponse response, int expectedCount) {
        if (response == null || response.getResults() == null || response.getResults().size() != expectedCount) {
            throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.EMBEDDING_PROTOCOL, false,
                    "Embedding 返回格式异常", "Embedding 返回结果数量不匹配", null);
        }
        List<List<Float>> vectors = response.getResults().stream().map(this::mapVector).toList();
        var metadata = response.getMetadata();
        Long promptTokens = metadata == null || metadata.getUsage() == null
                || metadata.getUsage().getPromptTokens() == null
                ? null : metadata.getUsage().getPromptTokens().longValue();
        Long totalTokens = metadata == null || metadata.getUsage() == null
                || metadata.getUsage().getTotalTokens() == null
                ? null : metadata.getUsage().getTotalTokens().longValue();
        Object rawRequestId = metadata == null ? null : metadata.get("providerRequestId");
        String requestId = rawRequestId == null ? null : rawRequestId.toString();
        return new EmbeddingBatchResult(vectors,
                metadata == null || metadata.getModel() == null || metadata.getModel().isBlank()
                        ? properties.getModel() : metadata.getModel(),
                promptTokens, totalTokens, requestId);
    }

    private List<Float> mapVector(Embedding embedding) {
        float[] vector = embedding.getOutput();
        if (vector == null || vector.length != properties.getDimension()) {
            throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.DIMENSION_MISMATCH, false,
                    "Embedding 向量维度不符合配置", "Embedding 向量维度不匹配", null);
        }
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private void validateInput(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.size() > properties.getMaxBatchSize()) {
            throw new IllegalArgumentException("Embedding 输入数量必须在 1 到 "
                    + properties.getMaxBatchSize() + " 之间");
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("Embedding 文本不能为空");
        }
    }

    private String sanitize(String text) {
        var result = contentSanitizer.sanitizeForProvider(text);
        if (result.status() == AiSanitizationStatus.BLOCKED) {
            throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.CONFIG, false,
                    "知识正文包含禁止发送的敏感信息", "Embedding 内容清洗器拦截了输入内容", null);
        }
        if (result.value() == null || result.value().isBlank()) {
            throw new IllegalArgumentException("清洗后 Embedding 文本为空");
        }
        return result.value();
    }
}
