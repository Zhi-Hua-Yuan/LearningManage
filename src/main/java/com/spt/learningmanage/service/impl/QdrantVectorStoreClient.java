package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.learningmanage.client.knowledge.KnowledgeRestTransport;
import com.spt.learningmanage.client.knowledge.RestTransportResponse;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.QdrantProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.dto.knowledge.VectorDocumentSnapshot;
import com.spt.learningmanage.model.dto.knowledge.VectorPayloadUpdate;
import com.spt.learningmanage.model.dto.knowledge.VectorPoint;
import com.spt.learningmanage.model.dto.knowledge.VectorPointMetadata;
import com.spt.learningmanage.model.dto.knowledge.VectorAccessFilter;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchRequest;
import com.spt.learningmanage.service.VectorStoreClient;
import com.spt.learningmanage.service.knowledge.KnowledgeDependencyType;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QdrantVectorStoreClient implements VectorStoreClient {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final QdrantProperties qdrant;
    private final EmbeddingProperties embedding;
    private final ObjectMapper objectMapper;
    private final KnowledgeResilientCallExecutor resilientCallExecutor;

    public QdrantVectorStoreClient(QdrantProperties qdrant,
                                   EmbeddingProperties embedding,
                                   ObjectMapper objectMapper,
                                   KnowledgeResilientCallExecutor resilientCallExecutor) {
        this.qdrant = qdrant;
        this.embedding = embedding;
        this.objectMapper = objectMapper;
        this.resilientCallExecutor = resilientCallExecutor;
    }

    @Override
    public void ensureCollection() {
        RestTransportResponse current = exchange(HttpMethod.GET,
                "/collections/" + qdrant.getCollection(), null);
        if (current.statusCode() == 404) {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode vectors = body.putObject("vectors");
            vectors.put("size", embedding.getDimension());
            vectors.put("distance", "Cosine");
            requireSuccess(exchange(HttpMethod.PUT, "/collections/" + qdrant.getCollection(), body.toString()));
        } else {
            requireSuccess(current);
            validateCollection(current.body());
        }
        ensurePayloadIndexes();
        ensureAlias();
    }

    @Override
    public void upsertPoints(List<VectorPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode array = body.putArray("points");
        for (VectorPoint point : points) {
            ObjectNode item = array.addObject();
            item.put("id", point.id());
            item.set("vector", objectMapper.valueToTree(point.vector()));
            item.set("payload", objectMapper.valueToTree(point.payload()));
        }
        requireSuccess(exchange(HttpMethod.PUT, dataPath("/points?wait=true&ordering=strong"), body.toString()));
    }

    @Override
    public void overwritePayload(List<VectorPayloadUpdate> updates) {
        if (updates == null) {
            return;
        }
        for (VectorPayloadUpdate update : updates) {
            ObjectNode body = objectMapper.createObjectNode();
            body.set("payload", objectMapper.valueToTree(update.payload()));
            body.putArray("points").add(update.pointId());
            requireSuccess(exchange(HttpMethod.PUT, dataPath("/points/payload?wait=true"), body.toString()));
        }
    }

    @Override
    public void deletePoints(List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode points = body.putArray("points");
        pointIds.forEach(points::add);
        requireSuccess(exchange(HttpMethod.POST,
                dataPath("/points/delete?wait=true&ordering=strong"), body.toString()));
    }

    @Override
    public void deleteByDocumentKey(String documentKey) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("filter", documentFilter(documentKey));
        requireSuccess(exchange(HttpMethod.POST,
                dataPath("/points/delete?wait=true&ordering=strong"), body.toString()));
    }

    @Override
    public VectorDocumentSnapshot inspectByDocumentKey(String documentKey) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("filter", documentFilter(documentKey));
        body.put("limit", 256);
        body.put("with_payload", true);
        body.put("with_vector", false);
        RestTransportResponse response = exchange(HttpMethod.POST, dataPath("/points/scroll"), body.toString());
        requireSuccess(response);
        try {
            JsonNode points = objectMapper.readTree(response.body()).path("result").path("points");
            List<VectorPointMetadata> result = new ArrayList<>();
            if (points.isArray()) {
                for (JsonNode point : points) {
                    String id = point.path("id").asText();
                    Map<String, Object> payload = objectMapper.convertValue(
                            point.path("payload"), new TypeReference<>() { });
                    result.add(new VectorPointMetadata(id, payload));
                }
            }
            return new VectorDocumentSnapshot(result);
        } catch (Exception exception) {
            throw failure(KnowledgeFailureTypeEnum.VECTOR_STORE, false,
                    "向量库返回格式异常", "Unable to parse Qdrant scroll response", exception);
        }
    }

    @Override
    public List<VectorSearchHit> query(VectorSearchRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("query", objectMapper.valueToTree(request.vector()));
        body.set("filter", accessFilter(request.accessFilter()));
        body.put("limit", request.limit());
        body.put("score_threshold", request.scoreThreshold());
        body.put("with_payload", true);
        body.put("with_vector", false);
        RestTransportResponse response = exchange(HttpMethod.POST, dataPath("/points/query"), body.toString());
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
            throw failure(KnowledgeFailureTypeEnum.VECTOR_STORE, false,
                    "向量库检索结果格式异常", "Unable to parse Qdrant query response", exception);
        }
    }

    private void validateCollection(String body) {
        try {
            JsonNode vectors = objectMapper.readTree(body)
                    .path("result").path("config").path("params").path("vectors");
            int size = vectors.path("size").asInt(-1);
            String distance = vectors.path("distance").asText("");
            if (size != embedding.getDimension() || !"cosine".equals(distance.toLowerCase(Locale.ROOT))) {
                throw failure(KnowledgeFailureTypeEnum.DIMENSION_MISMATCH, false,
                        "Qdrant Collection 配置与 Embedding 不兼容",
                        "Qdrant collection vector config mismatch", null);
            }
        } catch (KnowledgeIndexException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(KnowledgeFailureTypeEnum.VECTOR_STORE, false,
                    "Qdrant Collection 配置无法识别", "Unable to parse Qdrant collection config", exception);
        }
    }

    private void ensurePayloadIndexes() {
        Map<String, String> indexes = Map.of(
                "visibilityType", "keyword",
                "userId", "integer",
                "ownerUserId", "integer",
                "teamId", "integer",
                "projectId", "integer",
                "sourceType", "keyword",
                "sourceId", "integer",
                "sourceVersion", "keyword",
                "updatedAt", "datetime",
                "documentKey", "keyword"
        );
        for (Map.Entry<String, String> entry : indexes.entrySet()) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("field_name", entry.getKey());
            body.put("field_schema", entry.getValue());
            RestTransportResponse response = exchange(HttpMethod.PUT,
                    "/collections/" + qdrant.getCollection() + "/index?wait=true", body.toString());
            requireSuccess(response);
        }
    }

    private void ensureAlias() {
        RestTransportResponse response = exchange(HttpMethod.GET, "/aliases/" + qdrant.getAlias(), null);
        if (response.statusCode() == 404) {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode action = body.putArray("actions").addObject().putObject("create_alias");
            action.put("collection_name", qdrant.getCollection());
            action.put("alias_name", qdrant.getAlias());
            requireSuccess(exchange(HttpMethod.POST, "/collections/aliases", body.toString()));
            return;
        }
        requireSuccess(response);
        try {
            JsonNode aliases = objectMapper.readTree(response.body()).path("result").path("aliases");
            boolean matches = false;
            if (aliases.isArray()) {
                for (JsonNode alias : aliases) {
                    if (qdrant.getAlias().equals(alias.path("alias_name").asText())
                            && qdrant.getCollection().equals(alias.path("collection_name").asText())) {
                        matches = true;
                    }
                }
            }
            if (!matches) {
                throw failure(KnowledgeFailureTypeEnum.CONFIG, false,
                        "Qdrant Alias 指向了其他 Collection",
                        "Qdrant alias target mismatch", null);
            }
        } catch (KnowledgeIndexException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(KnowledgeFailureTypeEnum.VECTOR_STORE, false,
                    "Qdrant Alias 返回格式异常", "Unable to parse Qdrant alias response", exception);
        }
    }

    private ObjectNode documentFilter(String documentKey) {
        ObjectNode filter = objectMapper.createObjectNode();
        ObjectNode condition = filter.putArray("must").addObject();
        condition.put("key", "documentKey");
        condition.putObject("match").put("value", documentKey);
        return filter;
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
        ArrayNode teamMust = teamBranch.putArray("must");
        teamMust.add(match("visibilityType", "TEAM"));
        teamMust.add(match("teamId", access.teamId()));

        ObjectNode privateBranch = conditions.addObject();
        ArrayNode privateMust = privateBranch.putArray("must");
        privateMust.add(match("visibilityType", "PRIVATE"));
        privateMust.add(match("ownerUserId", access.actorUserId()));
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

    private RestTransportResponse exchange(HttpMethod method, String path, String body) {
        try {
            return resilientCallExecutor.execute(KnowledgeDependencyType.VECTOR_STORE,
                    () -> transport().exchange(method, path, body, false));
        } catch (KnowledgeRestTransport.TransportFailureException exception) {
            throw failure(KnowledgeFailureTypeEnum.VECTOR_STORE, true,
                    "向量库暂时不可用", "Qdrant HTTP transport failed", exception);
        }
    }

    private void requireSuccess(RestTransportResponse response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        boolean retryable = response.statusCode() == 404 || response.statusCode() == 408 || response.statusCode() == 429
                || response.statusCode() >= 500;
        throw failure(KnowledgeFailureTypeEnum.VECTOR_STORE, retryable,
                "向量库操作失败", "Qdrant returned HTTP " + response.statusCode(), null);
    }

    private KnowledgeRestTransport transport() {
        return new KnowledgeRestTransport(qdrant.getBaseUrl(), qdrant.getApiKey(),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    private KnowledgeIndexException failure(KnowledgeFailureTypeEnum type,
                                            boolean retryable,
                                            String safe,
                                            String internal,
                                            Throwable cause) {
        return new KnowledgeIndexException(type, retryable, safe, internal, cause);
    }
}
