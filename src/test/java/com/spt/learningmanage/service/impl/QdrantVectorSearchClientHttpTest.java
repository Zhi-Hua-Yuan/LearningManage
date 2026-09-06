package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.config.QdrantProperties;
import com.spt.learningmanage.model.dto.knowledge.VectorAccessFilter;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchRequest;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantVectorSearchClientHttpTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void queryBuildsPersonalPermissionFilterAndParsesHits() throws Exception {
        AtomicReference<String> requestBody = startServer("""
                {"result":{"points":[{"id":"point-1","score":0.91,"payload":{
                  "projectId":10,"visibilityType":"PRIVATE","ownerUserId":7,
                  "sourceType":"TASK","sourceId":20,"documentKey":"TASK:20:PRIVATE:10",
                  "chunkIndex":0,"sourceVersion":"v"
                }}]}}
                """);

        var hits = client().query(new VectorSearchRequest(
                List.of(0.1f, 0.2f, 0.3f), new VectorAccessFilter(10L, 7L, null), 20, 0.25));

        assertEquals(1, hits.size());
        assertEquals("point-1", hits.get(0).pointId());
        assertEquals(0.91, hits.get(0).score());
        String body = requestBody.get();
        assertTrue(body.contains("\"projectId\""));
        assertTrue(body.contains("\"query\":[0.1,0.2,0.3]"));
        assertFalse(body.contains("\"vector\""));
        assertTrue(body.contains("\"PRIVATE\""));
        assertTrue(body.contains("\"ownerUserId\""));
        assertTrue(body.contains("\"score_threshold\":0.25"));
        assertTrue(body.contains("\"with_vector\":false"));
    }

    @Test
    void queryBuildsTeamOrOwnPrivateBranches() throws Exception {
        AtomicReference<String> requestBody = startServer("{\"result\":{\"points\":[]}}");

        client().query(new VectorSearchRequest(
                List.of(0.1f, 0.2f, 0.3f), new VectorAccessFilter(10L, 7L, 99L), 20, 0.25));

        String body = requestBody.get();
        assertTrue(body.contains("\"min_should\""));
        assertTrue(body.contains("\"min_count\":1"));
        assertTrue(body.contains("\"TEAM\""));
        assertTrue(body.contains("\"teamId\""));
        assertTrue(body.contains("\"PRIVATE\""));
        assertTrue(body.contains("\"ownerUserId\""));
    }

    @Test
    void queryOmitsProviderThresholdWhenApplicationDisablesIt() throws Exception {
        AtomicReference<String> requestBody = startServer("{\"result\":{\"points\":[]}}");

        client().query(new VectorSearchRequest(
                List.of(0.1f, 0.2f, 0.3f), new VectorAccessFilter(10L, 7L, null), 20, -1));

        assertFalse(requestBody.get().contains("score_threshold"));
    }

    private AtomicReference<String> startServer(String responseBody) throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, responseBody);
        });
        server.start();
        return requestBody;
    }

    private QdrantVectorSearchClient client() {
        QdrantProperties qdrant = new QdrantProperties();
        qdrant.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        qdrant.setAlias("test_alias");
        return new QdrantVectorSearchClient(qdrant, new ObjectMapper(),
                new KnowledgeResilientCallExecutor(new KnowledgeIndexProperties()));
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
