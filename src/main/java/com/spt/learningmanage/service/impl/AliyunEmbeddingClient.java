package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.ai.governance.AiSanitizationStatus;
import com.spt.learningmanage.client.knowledge.KnowledgeRestTransport;
import com.spt.learningmanage.client.knowledge.RestTransportResponse;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.service.EmbeddingClient;
import com.spt.learningmanage.service.knowledge.KnowledgeDependencyType;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AliyunEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final AiContentSanitizer contentSanitizer;
    private final KnowledgeResilientCallExecutor resilientCallExecutor;

    public AliyunEmbeddingClient(EmbeddingProperties properties,
                                 ObjectMapper objectMapper,
                                 AiContentSanitizer contentSanitizer,
                                 KnowledgeResilientCallExecutor resilientCallExecutor) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.contentSanitizer = contentSanitizer;
        this.resilientCallExecutor = resilientCallExecutor;
    }

    @Override
    public EmbeddingBatchResult embedDocuments(List<String> texts, EmbeddingCallContext context) {
        validateInput(texts);
        List<String> sanitizedTexts = texts.stream().map(this::sanitize).toList();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", properties.getModel());
        request.put("dimensions", properties.getDimension());
        request.put("encoding_format", "float");
        ArrayNode input = request.putArray("input");
        sanitizedTexts.forEach(input::add);

        RestTransportResponse response;
        try {
            response = resilientCallExecutor.execute(KnowledgeDependencyType.EMBEDDING,
                    () -> {
                        RestTransportResponse value = transport().exchange(
                                HttpMethod.POST, "/embeddings", request.toString(), true);
                        requireSuccessful(value);
                        return value;
                    });
        } catch (KnowledgeRestTransport.TransportFailureException exception) {
            throw failure(KnowledgeFailureTypeEnum.NETWORK, true,
                    "Embedding 服务暂时不可用", "Embedding HTTP transport failed", exception);
        }
        return parse(response, texts.size());
    }

    private EmbeddingBatchResult parse(RestTransportResponse response, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != expectedCount) {
                throw new IllegalArgumentException("Embedding response item count mismatch");
            }
            List<IndexedVector> indexed = new ArrayList<>();
            for (int offset = 0; offset < data.size(); offset++) {
                JsonNode item = data.get(offset);
                int index = item.has("index") ? item.path("index").asInt(-1) : offset;
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray() || embedding.size() != properties.getDimension()) {
                    throw failure(KnowledgeFailureTypeEnum.DIMENSION_MISMATCH, false,
                            "Embedding 向量维度不符合配置",
                            "Expected dimension " + properties.getDimension() + " but received " + embedding.size(),
                            null);
                }
                List<Float> vector = new ArrayList<>(embedding.size());
                embedding.forEach(value -> vector.add((float) value.asDouble()));
                indexed.add(new IndexedVector(index, vector));
            }
            indexed.sort(Comparator.comparingInt(IndexedVector::index));
            for (int index = 0; index < indexed.size(); index++) {
                if (indexed.get(index).index() != index) {
                    throw new IllegalArgumentException("Embedding response indices are not contiguous");
                }
            }
            JsonNode usage = root.path("usage");
            Long promptTokens = nullableLong(usage, "prompt_tokens");
            Long totalTokens = nullableLong(usage, "total_tokens");
            String model = root.path("model").asText("").trim();
            if (model.isBlank()) {
                throw new IllegalArgumentException("Embedding provider did not identify the executed model");
            }
            String requestId = response.requestId();
            if ((requestId == null || requestId.isBlank()) && root.hasNonNull("id")) {
                requestId = root.path("id").asText();
            }
            return new EmbeddingBatchResult(
                    indexed.stream().map(IndexedVector::vector).toList(),
                    model, promptTokens, totalTokens, requestId
            );
        } catch (KnowledgeIndexException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(KnowledgeFailureTypeEnum.EMBEDDING_PROTOCOL, false,
                    "Embedding 返回格式异常", "Unable to parse embedding response", exception);
        }
    }

    private void validateInput(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.size() > properties.getMaxBatchSize()) {
            throw new IllegalArgumentException("Embedding input size must be between 1 and "
                    + properties.getMaxBatchSize());
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("Embedding text must not be blank");
        }
    }

    private String sanitize(String text) {
        var result = contentSanitizer.sanitizeForProvider(text);
        if (result.status() == AiSanitizationStatus.BLOCKED) {
            throw failure(KnowledgeFailureTypeEnum.CONFIG, false,
                    "知识正文包含禁止发送的敏感信息", "Embedding content sanitizer blocked input", null);
        }
        if (result.value() == null || result.value().isBlank()) {
            throw new IllegalArgumentException("Embedding text is empty after sanitization");
        }
        return result.value();
    }

    private void requireSuccessful(RestTransportResponse response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401 || status == 403) {
            throw failure(KnowledgeFailureTypeEnum.AUTH, false,
                    "Embedding 服务认证失败", "Embedding provider rejected credentials", null);
        }
        if (status == 429) {
            throw failure(KnowledgeFailureTypeEnum.RATE_LIMIT, true,
                    "Embedding 服务请求过多", "Embedding provider rate limited request", null);
        }
        if (status == 408 || status == 504) {
            throw failure(KnowledgeFailureTypeEnum.TIMEOUT, true,
                    "Embedding 服务响应超时", "Embedding provider timed out", null);
        }
        throw failure(KnowledgeFailureTypeEnum.NETWORK, status >= 500,
                "Embedding 服务调用失败", "Embedding provider returned HTTP " + status, null);
    }

    private KnowledgeRestTransport transport() {
        return new KnowledgeRestTransport(properties.getBaseUrl(), properties.getApiKey(),
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    private KnowledgeIndexException failure(KnowledgeFailureTypeEnum type,
                                            boolean retryable,
                                            String safe,
                                            String internal,
                                            Throwable cause) {
        return new KnowledgeIndexException(type, retryable, safe, internal, cause);
    }

    private Long nullableLong(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asLong() : null;
    }

    private record IndexedVector(int index, List<Float> vector) {
    }
}
