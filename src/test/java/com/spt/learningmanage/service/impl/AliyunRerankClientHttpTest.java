package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.spt.learningmanage.ai.governance.DefaultAiContentSanitizer;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.config.RerankProperties;
import com.spt.learningmanage.exception.RagDependencyException;
import com.spt.learningmanage.model.dto.rag.RerankCandidate;
import com.spt.learningmanage.model.dto.rag.RerankRequest;
import com.spt.learningmanage.service.rag.RagResilientCallExecutor;
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

class AliyunRerankClientHttpTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsCompatibleRequestAndMapsProviderIndexesToServerCandidateIds() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reranks", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("x-request-id", "rerank-1");
            respond(exchange, 200, """
                    {"model":"qwen3-rerank","results":[
                      {"index":1,"relevance_score":0.91},
                      {"index":0,"relevance_score":0.55}
                    ],"usage":{"total_tokens":9}}
                    """);
        });
        server.start();

        var result = client().rerank(new RerankRequest("为什么延期", List.of(
                new RerankCandidate("candidate-a", "任务A"),
                new RerankCandidate("candidate-b", "任务B")), 2, "trace"));

        assertEquals("candidate-b", result.items().get(0).candidateId());
        assertEquals(0.91, result.items().get(0).score());
        assertEquals(9L, result.inputTokens());
        assertEquals("rerank-1", result.providerRequestId());
        assertTrue(body.get().contains("\"top_n\":2"));
        assertTrue(body.get().contains("\"documents\":[\"任务A\",\"任务B\"]"));
    }

    @Test
    void rejectsDuplicateOrOutOfRangeProviderIndexes() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reranks", exchange -> respond(exchange, 200,
                "{\"results\":[{\"index\":4,\"relevance_score\":0.9}]}"));
        server.start();

        assertThrows(RagDependencyException.class, () -> client().rerank(new RerankRequest(
                "query", List.of(new RerankCandidate("a", "doc")), 1, "trace")));
    }

    private AliyunRerankClient client() {
        RerankProperties properties = new RerankProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        ObjectMapper mapper = new ObjectMapper();
        return new AliyunRerankClient(properties, mapper,
                new DefaultAiContentSanitizer(mapper, new AiProperties()),
                new RagResilientCallExecutor(properties));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
