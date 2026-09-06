package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.ai.governance.AiSanitizationStatus;
import com.spt.learningmanage.client.knowledge.KnowledgeRestTransport;
import com.spt.learningmanage.client.knowledge.RestTransportResponse;
import com.spt.learningmanage.config.RerankProperties;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.RagDependencyException;
import com.spt.learningmanage.model.dto.rag.RerankCandidate;
import com.spt.learningmanage.model.dto.rag.RerankItem;
import com.spt.learningmanage.model.dto.rag.RerankRequest;
import com.spt.learningmanage.model.dto.rag.RerankResult;
import com.spt.learningmanage.service.RerankClient;
import com.spt.learningmanage.service.rag.RagResilientCallExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AliyunRerankClient implements RerankClient {
    private final RerankProperties properties;
    private final ObjectMapper objectMapper;
    private final AiContentSanitizer sanitizer;
    private final RagResilientCallExecutor resilientCallExecutor;

    public AliyunRerankClient(RerankProperties properties,
                              ObjectMapper objectMapper,
                              AiContentSanitizer sanitizer,
                              RagResilientCallExecutor resilientCallExecutor) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sanitizer = sanitizer;
        this.resilientCallExecutor = resilientCallExecutor;
    }

    @Override
    public RerankResult rerank(RerankRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", required(properties.getModel(), "rerank model"));
        body.put("query", sanitize(request.query()));
        ArrayNode documents = body.putArray("documents");
        request.candidates().stream().map(RerankCandidate::text).map(this::sanitize)
                .forEach(documents::add);
        body.put("top_n", request.topN());
        if (properties.getInstruction() != null && !properties.getInstruction().isBlank()) {
            body.put("instruct", properties.getInstruction().trim());
        }

        RestTransportResponse response = resilientCallExecutor.executeRerank(() -> {
            RestTransportResponse value;
            try {
                value = transport().exchange(HttpMethod.POST, "/reranks", body.toString(), true);
            } catch (KnowledgeRestTransport.TransportFailureException exception) {
                throw failure(true, "重排服务暂时不可用", "Rerank HTTP transport failed", exception);
            }
            requireSuccessful(value);
            return value;
        });
        return parse(response, request.candidates());
    }

    private RerankResult parse(RestTransportResponse response, List<RerankCandidate> candidates) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                results = root.path("output").path("results");
            }
            if (!results.isArray()) {
                throw new IllegalArgumentException("Rerank response does not contain results");
            }
            List<RerankItem> items = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (JsonNode result : results) {
                int index = result.path("index").asInt(-1);
                double score = result.has("relevance_score")
                        ? result.path("relevance_score").asDouble(Double.NaN)
                        : result.path("score").asDouble(Double.NaN);
                if (index < 0 || index >= candidates.size() || !Double.isFinite(score) || !seen.add(index)) {
                    throw new IllegalArgumentException("Rerank result contains invalid index or score");
                }
                items.add(new RerankItem(candidates.get(index).candidateId(), index, score));
            }
            JsonNode usage = root.path("usage");
            Long inputTokens = usage.hasNonNull("input_tokens")
                    ? usage.path("input_tokens").asLong()
                    : usage.hasNonNull("total_tokens") ? usage.path("total_tokens").asLong() : null;
            String actualModel = root.path("model").asText("").trim();
            if (actualModel.isBlank()) {
                actualModel = properties.getModel();
            }
            String requestId = response.requestId();
            if ((requestId == null || requestId.isBlank()) && root.hasNonNull("id")) {
                requestId = root.path("id").asText();
            }
            return new RerankResult(items, actualModel, inputTokens, requestId);
        } catch (RagDependencyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(false, "重排服务返回格式异常", "Unable to parse rerank response", exception);
        }
    }

    private String sanitize(String text) {
        var result = sanitizer.sanitizeForProvider(text);
        if (result.status() == AiSanitizationStatus.BLOCKED) {
            throw failure(false, "内容包含禁止发送的敏感信息", "Rerank sanitizer blocked input", null);
        }
        return required(result.value(), "sanitized rerank content");
    }

    private void requireSuccessful(RestTransportResponse response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401 || status == 403) {
            throw failure(false, "重排服务认证失败", "Rerank credentials rejected", null);
        }
        boolean retryable = status == 408 || status == 429 || status >= 500;
        throw failure(retryable, "重排服务调用失败", "Rerank returned HTTP " + status, null);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw failure(false, "重排服务配置不完整", field + " must not be blank", null);
        }
        return value.trim();
    }

    private KnowledgeRestTransport transport() {
        return new KnowledgeRestTransport(properties.getBaseUrl(), properties.getApiKey(),
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    private RagDependencyException failure(boolean retryable,
                                           String safeMessage,
                                           String internalMessage,
                                           Throwable cause) {
        return new RagDependencyException(ErrorCode.RERANK_UNAVAILABLE, retryable,
                safeMessage, internalMessage, cause);
    }
}
