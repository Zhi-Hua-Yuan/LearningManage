package com.spt.learningmanage.client.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-owned Spring AI EmbeddingModel for the two DashScope protocols.
 * It deliberately keeps request-id and usage metadata that the stock OpenAI
 * model does not expose for every compatible provider.
 */
public final class SpringAiEmbeddingModel implements EmbeddingModel {

    public enum Mode { DOCUMENT, QUERY }

    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final KnowledgeRestTransport transport;
    private final Mode mode;

    public SpringAiEmbeddingModel(EmbeddingProperties properties,
                                  ObjectMapper objectMapper,
                                  Mode mode) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mode = mode;
        String baseUrl = mode == Mode.DOCUMENT ? properties.getBaseUrl() : properties.getQueryBaseUrl();
        this.transport = new KnowledgeRestTransport(baseUrl, properties.getApiKey(),
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request == null || request.getInstructions() == null
                ? List.of() : request.getInstructions();
        if (inputs.isEmpty() || (mode == Mode.QUERY && inputs.size() != 1)) {
            throw new IllegalArgumentException("Embedding 输入数量不符合当前模型协议");
        }
        String body = mode == Mode.DOCUMENT ? documentBody(inputs) : queryBody(inputs.get(0));
        String path = mode == Mode.DOCUMENT
                ? "/embeddings" : "/services/embeddings/text-embedding/text-embedding";
        RestTransportResponse response;
        try {
            response = transport.exchange(HttpMethod.POST, path, body, true);
        } catch (KnowledgeRestTransport.TransportFailureException exception) {
            throw failure(KnowledgeFailureTypeEnum.NETWORK, true,
                    "Embedding 服务暂时不可用", "Spring AI Embedding HTTP 传输失败", exception);
        }
        requireSuccessful(response);
        return parse(response, inputs.size());
    }

    @Override
    public float[] embed(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document 不能为空");
        }
        return embed(getEmbeddingContent(document));
    }

    @Override
    public int dimensions() {
        return properties.getDimension();
    }

    public float[] embed(String text) {
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(text), null));
        if (response.getResults().size() != 1) {
            throw new IllegalArgumentException("Embedding 返回结果数量异常");
        }
        return response.getResults().get(0).getOutput();
    }

    private String documentBody(List<String> inputs) {
        var root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("dimensions", properties.getDimension());
        root.put("encoding_format", "float");
        var values = root.putArray("input");
        inputs.forEach(values::add);
        return root.toString();
    }

    private String queryBody(String input) {
        var root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.putObject("input").putArray("texts").add(input);
        var parameters = root.putObject("parameters");
        parameters.put("dimension", properties.getDimension());
        parameters.put("text_type", "query");
        parameters.put("output_type", "dense");
        if (properties.getQueryInstruction() != null && !properties.getQueryInstruction().isBlank()) {
            parameters.put("instruct", properties.getQueryInstruction().trim());
        }
        return root.toString();
    }

    private EmbeddingResponse parse(RestTransportResponse response, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = mode == Mode.DOCUMENT ? root.path("data") : root.path("output").path("embeddings");
            if (!data.isArray()) {
                data = root.path("data");
            }
            if (!data.isArray() || data.size() != expectedCount) {
                throw new IllegalArgumentException("Embedding 返回结果数量不匹配");
            }
            List<IndexedVector> indexed = new ArrayList<>();
            for (int offset = 0; offset < data.size(); offset++) {
                JsonNode item = data.get(offset);
                int index = item.has("index") ? item.path("index").asInt(-1) : offset;
                JsonNode vectorNode = item.path("embedding");
                if (!vectorNode.isArray() || vectorNode.size() != properties.getDimension()) {
                    throw failure(KnowledgeFailureTypeEnum.DIMENSION_MISMATCH, false,
                            "Embedding 向量维度不符合配置", "Embedding 维度不匹配", null);
                }
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vectorNode.size(); i++) {
                    vector[i] = (float) vectorNode.get(i).asDouble();
                }
                indexed.add(new IndexedVector(index, vector));
            }
            indexed.sort(Comparator.comparingInt(IndexedVector::index));
            for (int index = 0; index < indexed.size(); index++) {
                if (indexed.get(index).index() != index) {
                    throw new IllegalArgumentException("Embedding 返回结果索引不连续");
                }
            }
            JsonNode usage = root.path("usage");
            Integer promptTokens = firstInt(usage, "prompt_tokens", "input_tokens", "total_tokens");
            Integer totalTokens = firstInt(usage, "total_tokens", "prompt_tokens", "input_tokens");
            String model = root.path("model").asText("").trim();
            if (model.isBlank()) {
                model = properties.getModel();
            }
            String requestId = response.requestId();
            if ((requestId == null || requestId.isBlank()) && root.hasNonNull("request_id")) {
                requestId = root.path("request_id").asText();
            }
            if ((requestId == null || requestId.isBlank()) && root.hasNonNull("id")) {
                requestId = root.path("id").asText();
            }
            Map<String, Object> metadata = new HashMap<>();
            if (requestId != null && !requestId.isBlank()) {
                metadata.put("providerRequestId", requestId);
            }
            return new EmbeddingResponse(
                    indexed.stream().map(value -> new Embedding(value.vector(), value.index())).toList(),
                    new EmbeddingResponseMetadata(model,
                            new DefaultUsage(promptTokens, 0, totalTokens), metadata));
        } catch (KnowledgeIndexException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(KnowledgeFailureTypeEnum.EMBEDDING_PROTOCOL, false,
                    "Embedding 返回格式异常", "无法解析 Spring AI Embedding 响应", exception);
        }
    }

    private void requireSuccessful(RestTransportResponse response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401 || status == 403) {
            throw failure(KnowledgeFailureTypeEnum.AUTH, false,
                    "Embedding 服务认证失败", "Embedding 服务拒绝了凭证", null);
        }
        if (status == 429) {
            throw failure(KnowledgeFailureTypeEnum.RATE_LIMIT, true,
                    "Embedding 服务请求过多", "Embedding 服务对请求做了限流", null);
        }
        if (status == 408 || status == 504) {
            throw failure(KnowledgeFailureTypeEnum.TIMEOUT, true,
                    "Embedding 服务响应超时", "Embedding 服务响应超时", null);
        }
        throw failure(KnowledgeFailureTypeEnum.NETWORK, status >= 500,
                "Embedding 服务调用失败", "Embedding 服务返回 HTTP " + status, null);
    }

    private Integer firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.hasNonNull(field)) {
                return node.path(field).asInt();
            }
        }
        return null;
    }

    private KnowledgeIndexException failure(KnowledgeFailureTypeEnum type,
                                            boolean retryable,
                                            String safe,
                                            String internal,
                                            Throwable cause) {
        return new KnowledgeIndexException(type, retryable, safe, internal, cause);
    }

    private record IndexedVector(int index, float[] vector) { }
}
