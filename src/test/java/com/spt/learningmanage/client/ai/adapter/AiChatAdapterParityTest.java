package com.spt.learningmanage.client.ai.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.client.ai.HutoolAiHttpTransport;
import com.spt.learningmanage.client.ai.spi.AiChatAdapter;
import com.spt.learningmanage.client.ai.spi.AiChatDispatchContext;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.config.SpringAiChatConfiguration;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs both transport adapters against the same deterministic OpenAI-compatible stub. */
class AiChatAdapterParityTest {

    private static final String COMPLETIONS_PATH = "/compatible-mode/v1/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private AiChatAdapter legacy;
    private AiChatAdapter springAi;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(COMPLETIONS_PATH, this::handle);
        server.start();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1");
        properties.setApiKey("parity-test-key");
        properties.setModel("ci-ai-stub");

        legacy = new LegacyAiChatAdapter(properties, new HutoolAiHttpTransport());
        SpringAiChatConfiguration configuration = new SpringAiChatConfiguration();
        ChatModel chatModel = configuration.springAiChatModel(
                configuration.springAiOpenAiApi(properties), properties);
        springAi = new SpringAiChatAdapter(chatModel, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void textResponse_isEquivalentAcrossAdapters() {
        assertParity(textCommand(), "deterministic text", List.of(), "stop");
    }

    @Test
    void forcedToolCall_isEquivalentAcrossAdapters() {
        AiChatCommand command = toolCommand();
        assertParity(command, null, List.of(AiToolCall.function("call-1", "query_tasks", "{\"limit\":2}")),
                "tool_calls");
    }

    @Test
    void toolResultRoundTrip_isEquivalentAcrossAdapters() {
        AiChatCommand command = new AiChatCommand(
                "ci-ai-stub",
                List.of(
                        AiChatMessage.user("查询任务"),
                        AiChatMessage.assistant("", List.of(
                                AiToolCall.function("call-1", "query_tasks", "{\"limit\":2}"))),
                        AiChatMessage.tool("call-1", "[{\"id\":1}]")
                ),
                List.of(tool()),
                AiToolChoice.auto(),
                0.0D,
                256);
        assertParity(command, "tool result accepted", List.of(), "stop");
    }

    private void assertParity(AiChatCommand command,
                              String expectedContent,
                              List<AiToolCall> expectedToolCalls,
                              String expectedFinishReason) {
        AiChatDispatchContext context = new AiChatDispatchContext(
                command.requestedModel(), 0, null,
                System.nanoTime() + 60_000_000_000L);
        AiChatResult legacyResult = legacy.chat(command, context);
        AiChatResult springAiResult = springAi.chat(command, context);

        assertEquals(expectedContent, legacyResult.content());
        assertEquals(expectedContent, springAiResult.content());
        assertEquals(expectedToolCalls, legacyResult.toolCalls());
        assertEquals(expectedToolCalls, springAiResult.toolCalls());
        assertEquals(expectedFinishReason, legacyResult.finishReason());
        assertEquals(expectedFinishReason, springAiResult.finishReason());
        assertEquals(legacyResult.usage(), springAiResult.usage());
        assertEquals(legacyResult.actualModel(), springAiResult.actualModel());
        assertNotNull(legacyResult.providerRequestId());
        assertNotNull(springAiResult.providerRequestId());
        assertFalse(legacyResult.providerRequestId().isBlank());
        assertFalse(springAiResult.providerRequestId().isBlank());
    }

    private AiChatCommand textCommand() {
        return new AiChatCommand("ci-ai-stub", List.of(AiChatMessage.user("普通文本")),
                List.of(), null, 0.0D, 256);
    }

    private AiChatCommand toolCommand() {
        return new AiChatCommand("ci-ai-stub", List.of(AiChatMessage.user("查任务")),
                List.of(tool()), AiToolChoice.function("query_tasks"), 0.0D, 256);
    }

    private AiToolDefinition tool() {
        var parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        parameters.putObject("properties").putObject("limit").put("type", "integer");
        return AiToolDefinition.function(new AiFunctionDefinition(
                "query_tasks", "查询任务", parameters));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean toolResult = request.contains("\"role\":\"tool\"");
        boolean toolCall = !toolResult && request.contains("\"tools\"");
        String body = toolCall ? toolCallBody() : textBody(toolResult ? "tool result accepted" : "deterministic text");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private String textBody(String content) {
        return "{\"id\":\"parity-response-id\",\"model\":\"ci-ai-stub-provider\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
                + quote(content) + "},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}";
    }

    private String toolCallBody() {
        return "{\"id\":\"parity-response-id\",\"model\":\"ci-ai-stub-provider\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{"
                + "\"name\":\"query_tasks\",\"arguments\":\"{\\\"limit\\\":2}\"}}]},"
                + "\"finish_reason\":\"tool_calls\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}";
    }

    private String quote(String value) {
        return objectMapper.valueToTree(value).toString();
    }
}
