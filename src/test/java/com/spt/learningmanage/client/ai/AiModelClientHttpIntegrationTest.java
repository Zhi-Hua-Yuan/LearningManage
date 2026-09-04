package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.impl.AiModelClientImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class AiModelClientHttpIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<JsonNode> receivedRequests = new CopyOnWriteArrayList<>();
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private AiModelClient aiModelClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", this::handleChatCompletion);
        server.start();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("integration-test-key");

        AiChatCommandValidator validator = new AiChatCommandValidator(objectMapper);
        AiChatRequestMapper requestMapper = new AiChatRequestMapper(objectMapper);
        AiChatResponseParser responseParser = new AiChatResponseParser(objectMapper);
        aiModelClient = new AiModelClientImpl(
                properties,
                new HutoolAiHttpTransport(),
                validator,
                requestMapper,
                responseParser
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chat_shouldCompleteToolRoundTripThroughRealHttpTransport() throws Exception {
        AiToolDefinition tool = AiToolDefinition.function(new AiFunctionDefinition(
                "query_tasks",
                "查询项目任务",
                objectMapper.readTree("{\"type\":\"object\",\"properties\":{}}")
        ));

        AiChatCommand firstCommand = new AiChatCommand(
                "qwen-plus",
                List.of(AiChatMessage.user("查询项目任务")),
                List.of(tool),
                AiToolChoice.auto(),
                0.2D,
                2000
        );
        AiChatResult toolCallResult = aiModelClient.chat(firstCommand);

        Assertions.assertEquals("qwen-plus-provider-snapshot", toolCallResult.actualModel());
        Assertions.assertEquals("provider-call-1", toolCallResult.providerRequestId());
        Assertions.assertEquals(1, toolCallResult.toolCalls().size());

        AiChatCommand secondCommand = new AiChatCommand(
                "qwen-plus",
                List.of(
                        AiChatMessage.user("查询项目任务"),
                        AiChatMessage.assistant(null, toolCallResult.toolCalls()),
                        AiChatMessage.tool(toolCallResult.toolCalls().get(0).id(), "[]")
                ),
                List.of(tool),
                AiToolChoice.auto(),
                0.2D,
                2000
        );
        AiChatResult finalResult = aiModelClient.chat(secondCommand);

        Assertions.assertEquals("工具结果已分析", finalResult.content());
        Assertions.assertEquals("qwen-plus-provider-snapshot", finalResult.actualModel());
        Assertions.assertEquals(2, receivedRequests.size());
        Assertions.assertEquals(0,
                receivedRequests.get(1).at("/messages/1/tool_calls/0/index").asInt());
        Assertions.assertEquals("call-1",
                receivedRequests.get(1).at("/messages/2/tool_call_id").asText());
        Assertions.assertEquals(List.of("Bearer integration-test-key", "Bearer integration-test-key"),
                authorizationHeaders);
    }

    private void handleChatCompletion(HttpExchange exchange) throws IOException {
        try (exchange) {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            receivedRequests.add(request);
            authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));

            int currentRequest = requestCount.incrementAndGet();
            String body = currentRequest == 1
                    ? """
                    {"id":"provider-call-1","model":"qwen-plus-provider-snapshot","choices":[{
                      "index":0,
                      "message":{"role":"assistant","content":null,"tool_calls":[{
                        "index":0,"id":"call-1","type":"function",
                        "function":{"name":"query_tasks","arguments":"{\\\"projectId\\\":1001}"}
                      }]},
                      "finish_reason":"tool_calls"
                    }]}
                    """
                    : """
                    {"id":"provider-call-2","model":"qwen-plus-provider-snapshot","choices":[{
                      "index":0,
                      "message":{"role":"assistant","content":"工具结果已分析"},
                      "finish_reason":"stop"
                    }]}
                    """;
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("X-Request-ID", "header-request-id");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        }
    }
}
