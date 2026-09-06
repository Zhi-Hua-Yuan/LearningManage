package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.QdrantProperties;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.dto.knowledge.VectorPayloadUpdate;
import com.spt.learningmanage.model.dto.knowledge.VectorPoint;
import com.spt.learningmanage.model.dto.knowledge.VectorAccessFilter;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchRequest;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantVectorStoreClientHttpTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void createsCollectionIndexesAliasAndUsesStableAliasForData() throws Exception {
        List<String> calls = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String call = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            calls.add(call);
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && path.equals("/collections/test_collection")) {
                respond(exchange, 404, "{\"status\":\"not_found\"}");
            } else if ("GET".equals(exchange.getRequestMethod()) && path.equals("/aliases/test_alias")) {
                respond(exchange, 404, "");
            } else if (path.endsWith("/points/scroll")) {
                respond(exchange, 200, "{\"result\":{\"points\":[{\"id\":\"point-1\",\"payload\":{\"documentKey\":\"TASK:1:PRIVATE:2\"}}]}}");
            } else {
                respond(exchange, 200, "{\"status\":\"ok\",\"result\":{}}");
            }
        });
        server.start();

        QdrantVectorStoreClient client = client(3);
        client.ensureCollection();
        client.upsertPoints(List.of(new VectorPoint("point-1", List.of(0.1f, 0.2f, 0.3f),
                Map.of("documentKey", "TASK:1:PRIVATE:2"))));
        client.overwritePayload(List.of(new VectorPayloadUpdate("point-1", Map.of("status", 1))));
        assertEquals(1, client.inspectByDocumentKey("TASK:1:PRIVATE:2").points().size());
        client.deletePoints(List.of("point-1"));
        client.deleteByDocumentKey("TASK:1:PRIVATE:2");

        assertTrue(calls.contains("PUT /collections/test_collection"));
        assertEquals(10, calls.stream().filter(call -> call.equals("PUT /collections/test_collection/index")).count());
        assertTrue(calls.contains("POST /collections/aliases"));
        assertTrue(calls.contains("PUT /collections/test_alias/points"));
        assertTrue(calls.contains("PUT /collections/test_alias/points/payload"));
        assertTrue(calls.contains("POST /collections/test_alias/points/scroll"));
        assertTrue(calls.contains("POST /collections/test_alias/points/delete"));
    }

    @Test
    void rejectsExistingCollectionWithWrongDimension() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 200,
                "{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":2,\"distance\":\"Cosine\"}}}}}"));
        server.start();

        KnowledgeIndexException exception = assertThrows(KnowledgeIndexException.class,
                () -> client(3).ensureCollection());
        assertEquals(KnowledgeFailureTypeEnum.DIMENSION_MISMATCH, exception.getFailureType());
    }

    @Test
    void queryBuildsPersonalPermissionFilterAndParsesHits() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"result":{"points":[{"id":"point-1","score":0.91,"payload":{
                      "projectId":10,"visibilityType":"PRIVATE","ownerUserId":7,
                      "sourceType":"TASK","sourceId":20,"documentKey":"TASK:20:PRIVATE:10",
                      "chunkIndex":0,"sourceVersion":"v"
                    }}]}}
                    """);
        });
        server.start();

        var hits = client(3).query(new VectorSearchRequest(
                List.of(0.1f, 0.2f, 0.3f), new VectorAccessFilter(10L, 7L, null), 20, 0.25));

        assertEquals(1, hits.size());
        assertEquals("point-1", hits.get(0).pointId());
        assertEquals(0.91, hits.get(0).score());
        String body = requestBody.get();
        assertTrue(body.contains("\"projectId\""));
        assertTrue(body.contains("\"visibilityType\""));
        assertTrue(body.contains("\"PRIVATE\""));
        assertTrue(body.contains("\"ownerUserId\""));
        assertTrue(body.contains("\"score_threshold\":0.25"));
        assertTrue(body.contains("\"with_vector\":false"));
    }

    @Test
    void queryBuildsTeamOrOwnPrivateBranches() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"result\":{\"points\":[]}}");
        });
        server.start();

        client(3).query(new VectorSearchRequest(
                List.of(0.1f, 0.2f, 0.3f), new VectorAccessFilter(10L, 7L, 99L), 20, 0.25));

        String body = requestBody.get();
        assertTrue(body.contains("\"min_should\""));
        assertTrue(body.contains("\"min_count\":1"));
        assertTrue(body.contains("\"TEAM\""));
        assertTrue(body.contains("\"teamId\""));
        assertTrue(body.contains("\"PRIVATE\""));
        assertTrue(body.contains("\"ownerUserId\""));
    }

    private QdrantVectorStoreClient client(int dimension) {
        QdrantProperties qdrant = new QdrantProperties();
        qdrant.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        qdrant.setCollection("test_collection");
        qdrant.setAlias("test_alias");
        EmbeddingProperties embedding = new EmbeddingProperties();
        embedding.setDimension(dimension);
        return new QdrantVectorStoreClient(qdrant, embedding, new ObjectMapper(),
                new KnowledgeResilientCallExecutor(new KnowledgeIndexProperties()));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
