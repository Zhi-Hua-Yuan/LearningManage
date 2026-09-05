package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class AiChatResponseParserTest {

    private AiChatResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiChatResponseParser(new ObjectMapper());
    }

    @Test
    void parse_shouldReadTextUsageAndProviderMetadata() {
        AiHttpResponse response = response("""
                {"id":"body-id","model":"provider-fallback-snapshot","unknown":"ignored","choices":[{
                  "message":{"role":"assistant","content":"完成"},"finish_reason":"stop"
                }],"usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14}}
                """, Map.of("X-Request-ID", List.of("header-id")));

        AiChatResult result = parser.parse(response, "primary", "fallback", 1, AiFailureTypeEnum.TIMEOUT);

        Assertions.assertEquals("完成", result.content());
        Assertions.assertEquals("stop", result.finishReason());
        Assertions.assertEquals(10, result.usage().promptTokens());
        Assertions.assertEquals("body-id", result.providerRequestId());
        Assertions.assertEquals("primary", result.requestedModel());
        Assertions.assertEquals("provider-fallback-snapshot", result.actualModel());
        Assertions.assertTrue(result.fallbackUsed());
        Assertions.assertEquals(AiFailureTypeEnum.TIMEOUT, result.fallbackReason());
    }

    @Test
    void parse_shouldReadMultipleToolCallsAndNullableContent() {
        AiChatResult result = parser.parse(response("""
                {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                  {"id":"call-1","type":"function","function":{"name":"query_tasks","arguments":"{\\"projectId\\":1}"}},
                  {"id":"call-2","type":"function","function":{"name":"query_stats","arguments":"{}"}}
                ]},"finish_reason":"tool_calls"}]}
                """, Map.of()), "model", "model", 0, null);

        Assertions.assertNull(result.content());
        Assertions.assertEquals(2, result.toolCalls().size());
        Assertions.assertEquals("query_tasks", result.toolCalls().get(0).function().name());
        Assertions.assertNull(result.usage());

        AiChatResult dashScopeCompatibilityResult = parser.parse(response(
                toolResponse("call-3", "function", "query_tasks", "{}", "stop"), Map.of()),
                "model", "model", 0, null);
        Assertions.assertEquals("stop", dashScopeCompatibilityResult.finishReason());
        Assertions.assertEquals(1, dashScopeCompatibilityResult.toolCalls().size());
    }

    @Test
    void parse_shouldUseRequestIdHeadersCaseInsensitivelyByPriority() {
        Map<String, List<String>> headers = Map.of(
                "REQUEST-ID", List.of("third"),
                "X-DashScope-Request-ID", List.of("second"),
                "X-REQUEST-ID", List.of("first")
        );
        AiChatResult result = parser.parse(response(textResponseWithoutId(), headers),
                "model", "model", 0, null);

        Assertions.assertEquals("first", result.providerRequestId());
    }

    @Test
    void parse_shouldKeepMissingOrPartialUsageAsNullable() {
        AiChatResult missing = parser.parse(response(textResponseWithoutId(), Map.of()),
                "model", "model", 0, null);
        AiChatResult partial = parser.parse(response("""
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":3}}
                """, Map.of()), "model", "model", 0, null);

        Assertions.assertNull(missing.usage());
        Assertions.assertEquals(3, partial.usage().promptTokens());
        Assertions.assertNull(partial.usage().completionTokens());
        Assertions.assertNull(partial.usage().totalTokens());
    }

    @Test
    void parse_shouldRejectMalformedOrStructurallyInvalidResponses() {
        assertInvalid("not-json");
        assertInvalid("{}");
        assertInvalid("{\"choices\":[]}");
        assertInvalid("{\"choices\":[{}]}");
        assertInvalid("{\"choices\":[{\"message\":{\"content\":\"   \"}}]}");
        assertInvalid("{\"choices\":[{\"message\":{\"content\":[]}}]}");
        assertInvalid("{\"choices\":[{\"message\":{\"role\":\"user\",\"content\":\"x\"}}]}");
    }

    @Test
    void parse_shouldRejectInvalidToolCalls() {
        assertInvalid(toolResponse("call-1", "custom", "query_tasks", "{}", "tool_calls"));
        assertInvalid(toolResponse("call-1", "function", "invalid name", "{}", "tool_calls"));
        assertInvalid(toolResponse("call-1", "function", "query_tasks", "not-json", "tool_calls"));
        assertInvalid(toolResponse("call-1", "function", "query_tasks", "[]", "tool_calls"));
        assertInvalid(toolResponse("call-1", "function", "query_tasks", "{}", "length"));
    }

    @Test
    void parse_shouldRejectFinishReasonToolCallsWithoutCalls() {
        assertInvalid("""
                {"choices":[{"message":{"content":"text"},"finish_reason":"tool_calls"}]}
                """);
    }

    @Test
    void parse_shouldRejectDuplicateToolIds() {
        assertInvalid("""
                {"choices":[{"message":{"content":null,"tool_calls":[
                  {"id":"call-1","type":"function","function":{"name":"a","arguments":"{}"}},
                  {"id":"call-1","type":"function","function":{"name":"b","arguments":"{}"}}
                ]},"finish_reason":"tool_calls"}]}
                """);
    }

    @Test
    void parse_shouldRejectInvalidUsageValues() {
        assertInvalid("""
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":-1}}
                """);
        assertInvalid("""
                {"choices":[{"message":{"content":"ok"}}],"usage":{"total_tokens":1.5}}
                """);
    }

    @Test
    void aiHttpResponse_shouldDropSensitiveHeadersAndDefensivelyCopy() {
        AiHttpResponse response = response(textResponseWithoutId(), Map.of(
                "Authorization", List.of("Bearer secret"),
                "Set-Cookie", List.of("secret-cookie"),
                "X-Request-ID", List.of("safe-id")
        ));

        Assertions.assertFalse(response.headers().containsKey("Authorization"));
        Assertions.assertFalse(response.headers().containsKey("Set-Cookie"));
        Assertions.assertEquals("safe-id", response.firstHeader("x-request-id"));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> response.headers().put("x", List.of("y")));
    }

    private void assertInvalid(String body) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> parser.parse(response(body, Map.of()), "model", "model", 0, null));
    }

    private AiHttpResponse response(String body, Map<String, List<String>> headers) {
        return new AiHttpResponse(200, body, headers);
    }

    private String textResponseWithoutId() {
        return "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    }

    private String toolResponse(String id, String type, String name, String arguments, String finishReason) {
        return "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{"
                + "\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"function\":{"
                + "\"name\":\"" + name + "\",\"arguments\":\"" + arguments.replace("\"", "\\\"")
                + "\"}}]},\"finish_reason\":\"" + finishReason + "\"}]}";
    }
}
