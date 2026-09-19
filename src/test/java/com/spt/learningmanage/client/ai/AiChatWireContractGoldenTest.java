package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.JacksonConfig;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI 线上协议的黄金用例。
 *
 * <p>它把「发出去的请求体」和「解析回来的结果」两端都钉死在冻结样本上，目的是让
 * Spring Boot 3.5 / Jackson 升级后，AI 的线上协议若发生任何形状变化都能被证伪：
 * 新增字段、{@code null} 的表示方式、结构差异都会让本用例失败。
 *
 * <p>请求侧用 {@link JsonNode#equals(Object)} 做逐节点比对，因此对键序不敏感，
 * 但对节点集合、类型与取值敏感。
 *
 * <p>冻结样本放在 {@code src/test/resources/ai-wire-golden/} 下，是可直接审阅的 JSON
 * 文件，而不是嵌在 Java 字符串里——后者会被 Java 自身的转义规则二次处理，
 * 反而看不清协议的真实形状。
 *
 * <p>与既有 {@code AiChatRequestMapperTest} 的区别：后者只断言若干字段的取值，
 * 不钉整棵请求树的形状。
 */
class AiChatWireContractGoldenTest {

    private static final String GOLDEN_DIR = "ai-wire-golden/";

    private ObjectMapper objectMapper;
    private AiChatRequestMapper mapper;
    private AiChatResponseParser parser;

    @BeforeEach
    void setUp() {
        // 用与生产一致的 ObjectMapper 构造路径（含 JacksonConfig 的 Long→String 定制），
        // 避免黄金用例跑在与线上不同的序列化配置上。
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
        mapper = new AiChatRequestMapper(objectMapper);
        parser = new AiChatResponseParser(objectMapper);
    }

    @Test
    void requestWireContractShouldMatchFrozenTreeForFullToolCommand() throws Exception {
        AiToolCall firstCall = AiToolCall.function("call-1", "query_tasks", "{\"projectId\":1001}");
        AiToolCall secondCall = AiToolCall.function("call-2", "query_tasks", "{\"projectId\":1002}");
        AiToolDefinition tool = AiToolDefinition.function(new AiFunctionDefinition(
                "query_tasks",
                "查询任务",
                objectMapper.readTree("{\"type\":\"object\","
                        + "\"properties\":{\"projectId\":{\"type\":\"integer\"}},"
                        + "\"required\":[\"projectId\"]}")));
        AiChatCommand command = new AiChatCommand(
                "qwen-plus",
                List.of(
                        AiChatMessage.system("系统\n提示"),
                        AiChatMessage.user("项目“甲”"),
                        AiChatMessage.assistant(null, List.of(firstCall, secondCall)),
                        AiChatMessage.tool("call-1", "[]"),
                        AiChatMessage.tool("call-2", "[]")),
                List.of(tool),
                AiToolChoice.function("query_tasks"),
                0.2D,
                2000);

        JsonNode actual = objectMapper.readTree(mapper.toJson(command));
        JsonNode expected = objectMapper.readTree(readGolden("request-full-tool-command.json"));

        Assertions.assertEquals(expected, actual, "AI Chat 请求线协议已偏离冻结样本");
    }

    @Test
    void requestWireContractShouldMatchFrozenTreeForPlainTextCommand() throws Exception {
        AiChatCommand command = new AiChatCommand(
                "qwen-plus",
                List.of(AiChatMessage.user("今天有什么任务？")),
                List.of(),
                null,
                null,
                null);

        JsonNode actual = objectMapper.readTree(mapper.toJson(command));
        JsonNode expected = objectMapper.readTree(readGolden("request-plain-text-command.json"));

        Assertions.assertEquals(expected, actual, "无工具请求的线协议已偏离冻结样本");
    }

    @Test
    void responseWireContractShouldMapEveryMetadataField() throws Exception {
        AiChatResult result = parser.parse(
                new AiHttpResponse(200, readGolden("response-tool-call.json")),
                "qwen-plus", "qwen-plus-fallback", 0, null);

        Assertions.assertNull(result.content());
        Assertions.assertEquals(
                List.of(AiToolCall.function("call-1", "query_tasks", "{\"projectId\":1001}")),
                result.toolCalls());
        Assertions.assertEquals("tool_calls", result.finishReason());
        Assertions.assertEquals(new AiUsage(11, 22, 33), result.usage());
        Assertions.assertEquals("req-abc-123", result.providerRequestId());
        Assertions.assertEquals("qwen-plus", result.requestedModel());
        Assertions.assertEquals("qwen-plus-2026-05-01", result.actualModel());
        Assertions.assertEquals(0, result.retryCount());
        Assertions.assertFalse(result.fallbackUsed());
        Assertions.assertNull(result.fallbackReason());
    }

    @Test
    void responseWireContractShouldFallBackToResponseHeaderRequestId() throws Exception {
        AiHttpResponse response = new AiHttpResponse(
                200,
                readGolden("response-plain-text.json"),
                Map.of("x-dashscope-request-id", List.of("dash-xyz")));

        AiChatResult result = parser.parse(response, "qwen-plus", null, 0, null);

        Assertions.assertEquals("dash-xyz", result.providerRequestId());
        Assertions.assertEquals("好的", result.content());
        Assertions.assertEquals("qwen-plus", result.actualModel());
        Assertions.assertTrue(result.toolCalls().isEmpty());
    }

    @Test
    void responseWireContractShouldExposeFallbackMetadata() throws Exception {
        AiChatResult result = parser.parse(
                new AiHttpResponse(200, readGolden("response-fallback.json")),
                "qwen-plus", "qwen-turbo", 1, AiFailureTypeEnum.TIMEOUT);

        Assertions.assertTrue(result.fallbackUsed());
        Assertions.assertEquals(AiFailureTypeEnum.TIMEOUT, result.fallbackReason());
        Assertions.assertEquals(1, result.retryCount());
        Assertions.assertEquals("req-fallback-1", result.providerRequestId());
        Assertions.assertEquals("qwen-turbo", result.actualModel());
    }

    @Test
    void responseWireContractShouldRejectPayloadWithoutChoices() throws Exception {
        String body = readGolden("response-missing-choices.json");

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(new AiHttpResponse(200, body), "qwen-plus", null, 0, null));

        Assertions.assertTrue(error.getMessage().contains("choices"));
    }

    private String readGolden(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(GOLDEN_DIR + name)) {
            Assertions.assertNotNull(stream, "缺少冻结样本: " + GOLDEN_DIR + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
