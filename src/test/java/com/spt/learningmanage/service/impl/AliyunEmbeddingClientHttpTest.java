package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.spt.learningmanage.ai.governance.DefaultAiContentSanitizer;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunEmbeddingClientHttpTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOpenAiCompatibleRequestAndParsesOrderedVectors() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = server(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "embedding-request-1");
            respond(exchange, 200, """
                    {"model":"text-embedding-v4","data":[
                      {"index":1,"embedding":[0.4,0.5,0.6]},
                      {"index":0,"embedding":[0.1,0.2,0.3]}
                    ],"usage":{"prompt_tokens":7,"total_tokens":7}}
                    """);
        });

        AliyunEmbeddingClient client = client(3);
        var result = client.embedDocuments(List.of("first", "second"),
                new EmbeddingCallContext(1L, "trace", List.of("a", "b")));

        assertEquals(List.of(0.1f, 0.2f, 0.3f), result.vectors().get(0));
        assertEquals(7L, result.promptTokens());
        assertEquals("embedding-request-1", result.providerRequestId());
        assertTrue(requestBody.get().contains("\"dimensions\":3"));
        assertTrue(requestBody.get().contains("\"encoding_format\":\"float\""));
        assertTrue(!requestBody.get().contains("text_type"));
    }

    @Test
    void rejectsWrongVectorDimension() throws Exception {
        server = server(exchange -> respond(exchange, 200,
                "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}"));

        KnowledgeIndexException exception = assertThrows(KnowledgeIndexException.class,
                () -> client(3).embedDocuments(List.of("text"),
                        new EmbeddingCallContext(1L, "trace", List.of("hash"))));
        assertEquals(KnowledgeFailureTypeEnum.DIMENSION_MISMATCH, exception.getFailureType());
    }

    @Test
    void classifiesRateLimitAsRetryable() throws Exception {
        server = server(exchange -> respond(exchange, 429, "{\"error\":\"rate_limit\"}"));

        KnowledgeIndexException exception = assertThrows(KnowledgeIndexException.class,
                () -> client(3).embedDocuments(List.of("text"),
                        new EmbeddingCallContext(1L, "trace", List.of("hash"))));
        assertEquals(KnowledgeFailureTypeEnum.RATE_LIMIT, exception.getFailureType());
        assertTrue(exception.isRetryable());
    }

    @Test
    void rejectsResponseThatDoesNotIdentifyExecutedModel() throws Exception {
        server = server(exchange -> respond(exchange, 200,
                "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}"));

        KnowledgeIndexException exception = assertThrows(KnowledgeIndexException.class,
                () -> client(3).embedDocuments(List.of("text"),
                        new EmbeddingCallContext(1L, "trace", List.of("hash"))));
        assertEquals(KnowledgeFailureTypeEnum.EMBEDDING_PROTOCOL, exception.getFailureType());
    }

    private AliyunEmbeddingClient client(int dimension) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setDimension(dimension);
        AiProperties aiProperties = new AiProperties();
        ObjectMapper mapper = new ObjectMapper();
        return new AliyunEmbeddingClient(properties, mapper,
                new DefaultAiContentSanitizer(mapper, aiProperties),
                new KnowledgeResilientCallExecutor(new KnowledgeIndexProperties()));
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer value = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        value.createContext("/embeddings", exchange -> handler.handle(exchange));
        value.start();
        return value;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
