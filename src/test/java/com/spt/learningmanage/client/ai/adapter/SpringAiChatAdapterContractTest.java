package com.spt.learningmanage.client.ai.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.client.ai.spi.AiChatDispatchContext;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.config.SpringAiChatConfiguration;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Spring AI 适配器的六字段映射契约测试（Phase 1 方案 §3.5 / §3.6）。
 *
 * <p>按方案要求「第一步先落契约测试，用真实 stub 响应把六个字段的取值路径钉死，
 * 先于任何业务接线」，本测试直接打真实的 HTTP 端点（进程内 HttpServer），
 * 并经由 {@link SpringAiChatConfiguration} 装配 ChatModel，
 * 因此它验证的是「配置 + 适配器」的联合行为，而不是被 mock 出来的假象。</p>
 */
class SpringAiChatAdapterContractTest {

    private static final String COMPLETIONS_PATH = "/compatible-mode/v1/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<String> receivedPaths = new CopyOnWriteArrayList<>();

    private final List<JsonNode> receivedBodies = new CopyOnWriteArrayList<>();

    private final List<String> receivedContentLengths = new CopyOnWriteArrayList<>();

    private HttpServer server;

    private SpringAiChatAdapter adapter;

    private Supplier<String> responseBodySupplier;

    private volatile int responseStatus = 200;

    @BeforeEach
    void setUp() throws IOException {
        responseBodySupplier = () -> happyPathBody();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 只注册真实路径：若适配器把 base-url 拼成了 /v1/v1/... 或漏掉 /v1，
        // 这里会 404，用例随即失败。
        server.createContext(COMPLETIONS_PATH, this::handle);
        server.start();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1");
        properties.setApiKey("contract-test-key");
        properties.setModel("qwen-plus");

        SpringAiChatConfiguration configuration = new SpringAiChatConfiguration();
        ChatModel chatModel = configuration.springAiChatModel(configuration.springAiOpenAiApi(properties), properties);
        adapter = new SpringAiChatAdapter(chatModel, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void request_shouldTargetCompatModeV1ChatCompletionsPath() {
        adapter.chat(textCommand(), context());

        Assertions.assertEquals(List.of(COMPLETIONS_PATH), receivedPaths,
                "Spring AI 侧必须请求 base-url 原值 + /chat/completions；"
                        + "默认的 /v1/chat/completions 会拼出 /v1/v1/ 造成 404");
        Assertions.assertNotNull(receivedContentLengths.get(0),
                "Spring AI 请求必须带固定 Content-Length，避免兼容网关把流式请求误判为空 body");
    }

    @Test
    void chat_shouldMapContentFinishReasonUsageActualModelAndProviderRequestId() {
        AiChatResult result = adapter.chat(textCommand(), context());

        Assertions.assertEquals("合同测试文本", result.content());
        Assertions.assertEquals("stop", result.finishReason());
        Assertions.assertEquals(120, result.usage().promptTokens());
        Assertions.assertEquals(80, result.usage().completionTokens());
        Assertions.assertEquals(200, result.usage().totalTokens());
        Assertions.assertEquals("qwen-plus-provider-snapshot", result.actualModel());
        Assertions.assertEquals("contract-response-id", result.providerRequestId());
        Assertions.assertTrue(result.toolCalls().isEmpty());
    }

    @Test
    void chat_shouldEchoDispatchContextBackIntoResult() {
        AiChatResult result = adapter.chat(textCommand(),
                new AiChatDispatchContext("qwen-max", 1, AiFailureTypeEnum.TIMEOUT, deadline()));

        Assertions.assertEquals("qwen-max", result.requestedModel());
        Assertions.assertEquals("qwen-plus-provider-snapshot", result.actualModel());
        Assertions.assertEquals(1, result.retryCount());
        Assertions.assertTrue(result.fallbackUsed());
        Assertions.assertEquals(AiFailureTypeEnum.TIMEOUT, result.fallbackReason());
    }

    @Test
    void chat_shouldMapToolCallsWithIdAndArguments() {
        responseBodySupplier = () -> toolCallBody();
        List<AiToolDefinition> tools = List.of(tool());

        AiChatResult result = adapter.chat(new AiChatCommand(
                "qwen-plus",
                List.of(AiChatMessage.user("查任务")),
                tools,
                AiToolChoice.function("query_tasks"),
                0.2D,
                2000
        ), context());

        Assertions.assertEquals("tool_calls", result.finishReason());
        Assertions.assertEquals(1, result.toolCalls().size());
        Assertions.assertEquals("call-abc", result.toolCalls().get(0).id());
        Assertions.assertEquals("function", result.toolCalls().get(0).type());
        Assertions.assertEquals("query_tasks", result.toolCalls().get(0).function().name());
        Assertions.assertEquals("{\"projectId\":1001}", result.toolCalls().get(0).function().arguments());
    }

    @Test
    void request_shouldCarryToolsToolChoiceAndNoParallelToolCalls() {
        responseBodySupplier = () -> toolCallBody();

        adapter.chat(new AiChatCommand(
                "qwen-plus",
                List.of(AiChatMessage.user("查任务")),
                List.of(tool()),
                AiToolChoice.function("query_tasks"),
                0.2D,
                2000
        ), context());

        JsonNode sent = receivedBodies.get(0);
        Assertions.assertEquals("qwen-plus", sent.get("model").asText());
        Assertions.assertEquals(1, sent.get("tools").size(), sent.toString());
        Assertions.assertEquals("query_tasks", sent.get("tools").get(0).get("function").get("name").asText(),
                sent.toString());
        Assertions.assertFalse(sent.get("parallel_tool_calls").asBoolean(true));
        Assertions.assertEquals("query_tasks",
                sent.get("tool_choice").get("function").get("name").asText());
        Assertions.assertEquals(0.2D, sent.get("temperature").asDouble());
        Assertions.assertEquals(2000, sent.get("max_tokens").asInt());
    }

    @Test
    void request_shouldRenderToolRoundTripWithToolCallIdAndFunctionName() {
        responseBodySupplier = () -> textBody("已分析");

        adapter.chat(new AiChatCommand(
                "qwen-plus",
                List.of(
                        AiChatMessage.user("查任务"),
                        AiChatMessage.assistant(null, List.of(
                                AiToolCall.function("call-abc", "query_tasks", "{\"projectId\":1001}"))),
                        AiChatMessage.tool("call-abc", "[{\"id\":1}]")
                ),
                List.of(tool()),
                AiToolChoice.auto(),
                null,
                null
        ), context());

        JsonNode messages = receivedBodies.get(0).get("messages");
        Assertions.assertEquals("assistant", messages.get(1).get("role").asText());
        Assertions.assertEquals("call-abc", messages.get(1).get("tool_calls").get(0).get("id").asText());
        Assertions.assertEquals("tool", messages.get(2).get("role").asText());
        Assertions.assertEquals("call-abc", messages.get(2).get("tool_call_id").asText());
    }

    @Test
    void chat_shouldRejectResponseWithoutUsage() {
        responseBodySupplier = () -> body("合同测试文本", "stop", null, "contract-response-id",
                "qwen-plus-provider-snapshot");

        assertInvalidResponse("AI 响应缺少 usage");
    }

    @Test
    void chat_shouldRejectResponseWithoutProviderRequestId() {
        responseBodySupplier = () -> body("合同测试文本", "stop", usageJson(), null,
                "qwen-plus-provider-snapshot");

        assertInvalidResponse("AI 响应缺少 providerRequestId");
    }

    @Test
    void chat_shouldRejectResponseWithoutActualModel() {
        responseBodySupplier = () -> body("合同测试文本", "stop", usageJson(), "contract-response-id", null);

        assertInvalidResponse("AI 响应缺少 actualModel");
    }

    @Test
    void chat_shouldRejectResponseWithEmptyContentAndNoToolCalls() {
        responseBodySupplier = () -> textBody(null);

        assertInvalidResponse("AI 响应 content 与 tool_calls 均为空");
    }

    @Test
    void chat_shouldClassifyHttpStatusesLikeLegacyAdapter() {
        assertStatusMapping(401, AiFailureTypeEnum.UPSTREAM_REJECTED);
        assertStatusMapping(429, AiFailureTypeEnum.RATE_LIMITED);
        assertStatusMapping(500, AiFailureTypeEnum.UPSTREAM_SERVER_ERROR);
        assertStatusMapping(504, AiFailureTypeEnum.TIMEOUT);
    }

    // ------------------------------------------------------------------

    private void assertInvalidResponse(String expectedInternalMessageFragment) {
        AiInvocationException exception = Assertions.assertThrows(AiInvocationException.class,
                () -> adapter.chat(textCommand(), context()));
        Assertions.assertEquals(AiFailureTypeEnum.INVALID_RESPONSE, exception.getFailureType());
        Assertions.assertTrue(exception.getMessage().contains(expectedInternalMessageFragment),
                "实际内部消息: " + exception.getMessage());
    }

    private void assertStatusMapping(int status, AiFailureTypeEnum expected) {
        responseStatus = status;
        AiInvocationException exception = Assertions.assertThrows(AiInvocationException.class,
                () -> adapter.chat(textCommand(), context()));
        Assertions.assertEquals(expected, exception.getFailureType(), "status=" + status);
        Assertions.assertEquals(status, (int) exception.getHttpStatusCode(), "status=" + status);
    }

    private AiChatCommand textCommand() {
        return new AiChatCommand("qwen-plus", List.of(AiChatMessage.user("你好")), List.of(), null, null, null);
    }

    private AiChatDispatchContext context() {
        return new AiChatDispatchContext("qwen-plus", 0, null, deadline());
    }

    private long deadline() {
        return System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    }

    private AiToolDefinition tool() {
        try {
            return AiToolDefinition.function(new AiFunctionDefinition(
                    "query_tasks",
                    "查询项目任务",
                    objectMapper.readTree("{\"type\":\"object\",\"properties\":{\"projectId\":{\"type\":\"integer\"}}}")
            ));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String usageJson() {
        return "{\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200}";
    }

    private String happyPathBody() {
        return body("合同测试文本", "stop", usageJson(), "contract-response-id", "qwen-plus-provider-snapshot");
    }

    private String textBody(String content) {
        return body(content, "stop", usageJson(), "contract-response-id", "qwen-plus-provider-snapshot");
    }

    private String toolCallBody() {
        return "{\"id\":\"contract-response-id\",\"object\":\"chat.completion\","
                + "\"model\":\"qwen-plus-provider-snapshot\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"index\":0,\"id\":\"call-abc\",\"type\":\"function\","
                + "\"function\":{\"name\":\"query_tasks\",\"arguments\":\"{\\\"projectId\\\":1001}\"}}]},"
                + "\"finish_reason\":\"tool_calls\"}],"
                + "\"usage\":" + usageJson() + "}";
    }

    private String body(String content, String finishReason, String usage, String id, String model) {
        List<String> fields = new ArrayList<>();
        if (id != null) {
            fields.add("\"id\":\"" + id + "\"");
        }
        if (model != null) {
            fields.add("\"model\":\"" + model + "\"");
        }
        fields.add("\"object\":\"chat.completion\"");
        fields.add("\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
                + (content == null ? "null" : "\"" + content + "\"") + "},\"finish_reason\":\""
                + finishReason + "\"}]");
        if (usage != null) {
            fields.add("\"usage\":" + usage);
        }
        return "{" + String.join(",", fields) + "}";
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedPaths.add(exchange.getRequestURI().getPath());
        receivedContentLengths.add(exchange.getRequestHeaders().getFirst("Content-Length"));
        receivedBodies.add(objectMapper.readTree(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8)));
        byte[] payload = responseBodySupplier.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
