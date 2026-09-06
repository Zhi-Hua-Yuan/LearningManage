package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.learningmanage.client.knowledge.KnowledgeRestTransport;
import com.spt.learningmanage.client.knowledge.RestTransportResponse;
import com.spt.learningmanage.config.QdrantProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.dto.knowledge.VectorAccessFilter;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchRequest;
import com.spt.learningmanage.service.VectorSearchClient;
import com.spt.learningmanage.service.knowledge.KnowledgeDependencyType;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QdrantVectorSearchClient implements VectorSearchClient {
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final QdrantProperties qdrant;
    private final ObjectMapper objectMapper;
    private final KnowledgeResilientCallExecutor resilientCallExecutor;

    public QdrantVectorSearchClient(QdrantProperties qdrant,
                                    ObjectMapper objectMapper,
                                    KnowledgeResilientCallExecutor resilientCallExecutor) {
        this.qdrant = qdrant;
        this.objectMapper = objectMapper;
        this.resilientCallExecutor = resilientCallExecutor;
    }

    @Override
    public List<VectorSearchHit> query(VectorSearchRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("vector", objectMapper.valueToTree(request.vector()));
        body.set("filter", accessFilter(request.accessFilter()));
        body.put("limit", request.limit());
        // A negative configured value is the application's explicit "disabled"
        // sentinel. Do not forward it as a provider threshold: omitting the
        // optional field is the only portable way to request the full top-K.
        if (request.scoreThreshold() >= 0) {
            body.put("score_threshold", request.scoreThreshold());
        }
        body.put("with_payload", true);
        body.put("with_vector", false);
        RestTransportResponse response = exchange(dataPath("/points/search"), body.toString());
        requireSuccess(response);
        try {
            JsonNode result = objectMapper.readTree(response.body()).path("result");
            JsonNode points = result.isArray() ? result : result.path("points");
            if (!points.isArray()) {
                throw new IllegalArgumentException("Qdrant query result does not contain points");
            }
            List<VectorSearchHit> hits = new ArrayList<>();
            for (JsonNode point : points) {
                Map<String, Object> payload = objectMapper.convertValue(
                        point.path("payload"), new TypeReference<>() { });
                hits.add(new VectorSearchHit(
                        point.path("id").asText(), point.path("score").asDouble(), payload));
            }
            return List.copyOf(hits);
        } catch (Exception exception) {
            throw failure(false, "向量库检索结果格式异常",
                    "Unable to parse Qdrant query response", exception);
        }
    }

    private ObjectNode accessFilter(VectorAccessFilter access) {
        ObjectNode filter = objectMapper.createObjectNode();
        ArrayNode must = filter.putArray("must");
        must.add(match("projectId", access.projectId()));
        if (!access.teamProject()) {
            must.add(match("visibilityType", "PRIVATE"));
            must.add(match("ownerUserId", access.actorUserId()));
            return filter;
        }
        ObjectNode minimum = filter.putObject("min_should");
        minimum.put("min_count", 1);
        ArrayNode conditions = minimum.putArray("conditions");
        ObjectNode teamBranch = conditions.addObject();
        teamBranch.putArray("must")
                .add(match("visibilityType", "TEAM"))
                .add(match("teamId", access.teamId()));
        ObjectNode privateBranch = conditions.addObject();
        privateBranch.putArray("must")
                .add(match("visibilityType", "PRIVATE"))
                .add(match("ownerUserId", access.actorUserId()));
        return filter;
    }

    private ObjectNode match(String key, Object value) {
        ObjectNode condition = objectMapper.createObjectNode();
        condition.put("key", key);
        ObjectNode match = condition.putObject("match");
        if (value instanceof Number number) {
            match.put("value", number.longValue());
        } else {
            match.put("value", String.valueOf(value));
        }
        return condition;
    }

    private String dataPath(String suffix) {
        return "/collections/" + qdrant.getAlias() + suffix;
    }

    private RestTransportResponse exchange(String path, String body) {
        try {
            return resilientCallExecutor.execute(KnowledgeDependencyType.VECTOR_STORE,
                    () -> transport().exchange(HttpMethod.POST, path, body, false));
        } catch (KnowledgeRestTransport.TransportFailureException exception) {
            throw failure(true, "向量库暂时不可用", "Qdrant query transport failed", exception);
        }
    }

    private void requireSuccess(RestTransportResponse response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        boolean retryable = response.statusCode() == 408 || response.statusCode() == 429
                || response.statusCode() >= 500;
        throw failure(retryable, "向量库检索失败",
                "Qdrant query returned HTTP " + response.statusCode(), null);
    }

    private KnowledgeRestTransport transport() {
        return new KnowledgeRestTransport(qdrant.getBaseUrl(), qdrant.getApiKey(),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    private KnowledgeIndexException failure(boolean retryable,
                                            String safe,
                                            String internal,
                                            Throwable cause) {
        return new KnowledgeIndexException(KnowledgeFailureTypeEnum.VECTOR_STORE,
                retryable, safe, internal, cause);
    }
}
