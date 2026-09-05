package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class AiChatRequestMapperTest {

    private ObjectMapper objectMapper;
    private AiChatRequestMapper mapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new AiChatRequestMapper(objectMapper);
    }

    @Test
    void toJson_shouldMapCompleteMultiTurnToolRequest() throws Exception {
        AiToolCall call = AiToolCall.function("call-1", "query_tasks", "{\"projectId\":1001}");
        AiToolCall secondCall = AiToolCall.function("call-2", "query_tasks", "{\"projectId\":1002}");
        AiToolDefinition tool = AiToolDefinition.function(new AiFunctionDefinition(
                "query_tasks", "查询任务", objectMapper.readTree("{\"type\":\"object\"}")));
        AiChatCommand command = new AiChatCommand(
                "qwen-plus",
                List.of(
                        AiChatMessage.system("系统\n提示"),
                        AiChatMessage.user("项目“甲”"),
                        AiChatMessage.assistant(null, List.of(call, secondCall)),
                        AiChatMessage.tool("call-1", "[]"),
                        AiChatMessage.tool("call-2", "[]")
                ),
                List.of(tool),
                AiToolChoice.function("query_tasks"),
                0.2D,
                2000
        );

        JsonNode json = objectMapper.readTree(mapper.toJson(command));

        Assertions.assertEquals("qwen-plus", json.get("model").asText());
        Assertions.assertFalse(json.get("stream").asBoolean());
        Assertions.assertFalse(json.get("parallel_tool_calls").asBoolean());
        Assertions.assertEquals("项目“甲”", json.at("/messages/1/content").asText());
        Assertions.assertTrue(json.at("/messages/2/content").isNull());
        Assertions.assertEquals(0, json.at("/messages/2/tool_calls/0/index").asInt());
        Assertions.assertEquals("call-1", json.at("/messages/2/tool_calls/0/id").asText());
        Assertions.assertEquals(1, json.at("/messages/2/tool_calls/1/index").asInt());
        Assertions.assertEquals("call-2", json.at("/messages/2/tool_calls/1/id").asText());
        Assertions.assertEquals("call-1", json.at("/messages/3/tool_call_id").asText());
        Assertions.assertEquals("call-2", json.at("/messages/4/tool_call_id").asText());
        Assertions.assertEquals("object", json.at("/tools/0/function/parameters/type").asText());
        Assertions.assertEquals("query_tasks", json.at("/tool_choice/function/name").asText());
        Assertions.assertEquals(2000, json.get("max_tokens").asInt());
    }

    @Test
    void toJson_shouldDefaultToolChoiceToAutoWhenToolsExist() throws Exception {
        AiToolDefinition tool = AiToolDefinition.function(new AiFunctionDefinition(
                "query_tasks", "查询任务", objectMapper.createObjectNode()));
        AiChatCommand command = new AiChatCommand(
                "model", List.of(AiChatMessage.user("x")), List.of(tool), null, null, null);

        JsonNode json = objectMapper.readTree(mapper.toJson(command));

        Assertions.assertEquals("auto", json.get("tool_choice").asText());
        Assertions.assertFalse(json.has("temperature"));
        Assertions.assertFalse(json.has("max_tokens"));
    }

    @Test
    void toJson_shouldOmitToolChoiceAndToolFieldsWithoutTools() throws Exception {
        AiChatCommand command = new AiChatCommand(
                "model", List.of(AiChatMessage.user("x")), List.of(), AiToolChoice.none(), null, null);

        JsonNode json = objectMapper.readTree(mapper.toJson(command));

        Assertions.assertFalse(json.has("tool_choice"));
        Assertions.assertFalse(json.has("tools"));
        Assertions.assertFalse(json.has("parallel_tool_calls"));
    }

    @Test
    void toJson_shouldMapNoneWhenToolsExist() throws Exception {
        AiToolDefinition tool = AiToolDefinition.function(new AiFunctionDefinition(
                "query_tasks", "查询任务", objectMapper.createObjectNode()));
        AiChatCommand command = new AiChatCommand(
                "model", List.of(AiChatMessage.user("x")), List.of(tool), AiToolChoice.none(), null, null);

        JsonNode json = objectMapper.readTree(mapper.toJson(command));

        Assertions.assertEquals("none", json.get("tool_choice").asText());
        Assertions.assertTrue(json.has("tools"));
        Assertions.assertFalse(json.get("parallel_tool_calls").asBoolean());
    }
}
