package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiMessageRole;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class AiChatCommandValidatorTest {

    private ObjectMapper objectMapper;
    private AiChatCommandValidator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new AiChatCommandValidator(objectMapper);
    }

    @Test
    void validate_shouldAcceptCompleteToolRoundTrip() throws Exception {
        AiToolCall toolCall = AiToolCall.function("call-1", "query_tasks", "{\"projectId\":1001}");
        AiChatCommand command = command(
                List.of(
                        AiChatMessage.system("你是项目助手"),
                        AiChatMessage.user("查询任务"),
                        AiChatMessage.assistant(null, List.of(toolCall)),
                        AiChatMessage.tool("call-1", "[]"),
                        AiChatMessage.assistant("没有待办任务")
                ),
                List.of(tool("query_tasks")),
                AiToolChoice.function("query_tasks")
        );

        Assertions.assertDoesNotThrow(() -> validator.validate(command));
    }

    @Test
    void validate_shouldRejectEmptyOrTooManyMessages() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validator.validate(command(List.of(), List.of(), null)));
        List<AiChatMessage> messages = new ArrayList<>(Collections.nCopies(65, AiChatMessage.user("x")));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validator.validate(command(messages, List.of(), null)));
    }

    @Test
    void validate_shouldRejectInvalidRoleFields() {
        AiChatMessage systemWithToolCall = new AiChatMessage(
                AiMessageRole.SYSTEM, "system", "call-1", List.of());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validator.validate(command(List.of(systemWithToolCall), List.of(), null)));

        AiChatMessage emptyAssistant = new AiChatMessage(AiMessageRole.ASSISTANT, null, null, List.of());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validator.validate(command(List.of(emptyAssistant), List.of(), null)));

        AiChatMessage toolWithoutContent = new AiChatMessage(AiMessageRole.TOOL, null, "call-1", List.of());
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validator.validate(command(List.of(toolWithoutContent), List.of(), null)));
    }

    @Test
    void validate_shouldRejectUnknownFutureAndRepeatedToolResult() {
        AiToolCall call = AiToolCall.function("call-1", "query_tasks", "{}");
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.tool("call-1", "[]"), AiChatMessage.assistant(null, List.of(call))),
                List.of(toolUnchecked("query_tasks")), null)));

        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(
                        AiChatMessage.assistant(null, List.of(call)),
                        AiChatMessage.tool("call-1", "[]"),
                        AiChatMessage.tool("call-1", "[]")
                ),
                List.of(toolUnchecked("query_tasks")), null)));
    }

    @Test
    void validate_shouldRejectUndeclaredOrUnresolvedHistoricalToolCall() {
        AiToolCall call = AiToolCall.function("call-1", "query_tasks", "{}");
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.assistant(null, List.of(call)), AiChatMessage.tool("call-1", "[]")),
                List.of(), null)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.assistant(null, List.of(call))),
                List.of(toolUnchecked("query_tasks")), null)));
    }

    @Test
    void validate_shouldRejectDuplicateToolCallIdsAndInvalidArguments() {
        AiToolCall first = AiToolCall.function("call-1", "query_tasks", "{}");
        AiToolCall duplicate = AiToolCall.function("call-1", "query_stats", "{}");
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.assistant(null, List.of(first, duplicate))), List.of(), null)));

        AiToolCall invalidArguments = AiToolCall.function("call-2", "query_tasks", "not-json");
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.assistant(null, List.of(invalidArguments))), List.of(), null)));
    }

    @Test
    void validate_shouldRejectDuplicateInvalidAndExcessiveTools() throws Exception {
        AiToolDefinition duplicate1 = tool("query_tasks");
        AiToolDefinition duplicate2 = tool("query_tasks");
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.user("x")), List.of(duplicate1, duplicate2), null)));

        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.user("x")), List.of(tool("invalid name")), null)));

        List<AiToolDefinition> tools = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            tools.add(tool("tool_" + i));
        }
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.user("x")), tools, null)));
    }

    @Test
    void validate_shouldRejectNonObjectSchemaAndUnsupportedToolType() throws Exception {
        AiToolDefinition arraySchema = AiToolDefinition.function(new AiFunctionDefinition(
                "query_tasks", "查询任务", objectMapper.readTree("[]")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.user("x")), List.of(arraySchema), null)));

        AiToolDefinition unsupported = new AiToolDefinition("custom", arraySchema.function());
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.user("x")), List.of(unsupported), null)));
    }

    @Test
    void validate_shouldRejectUnknownForcedFunctionAndInvalidChoiceShape() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.user("x")), List.of(tool("query_tasks")),
                AiToolChoice.function("query_stats"))));
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(command(
                List.of(AiChatMessage.user("x")), List.of(tool("query_tasks")),
                new AiToolChoice(AiToolChoice.Mode.AUTO, "query_tasks"))));
    }

    @Test
    void validate_shouldRejectInvalidGenerationOptions() {
        AiChatCommand temperature = new AiChatCommand(
                "model", List.of(AiChatMessage.user("x")), List.of(), null, 2.0D, null);
        AiChatCommand maximumValidTemperature = new AiChatCommand(
                "model", List.of(AiChatMessage.user("x")), List.of(), null, 1.999D, null);
        AiChatCommand tokens = new AiChatCommand(
                "model", List.of(AiChatMessage.user("x")), List.of(), null, null, 0);
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(temperature));
        Assertions.assertDoesNotThrow(() -> validator.validate(maximumValidTemperature));
        Assertions.assertThrows(IllegalArgumentException.class, () -> validator.validate(tokens));
    }

    private AiChatCommand command(List<AiChatMessage> messages,
                                  List<AiToolDefinition> tools,
                                  AiToolChoice toolChoice) {
        return new AiChatCommand("qwen-plus", messages, tools, toolChoice, 0.2D, 2000);
    }

    private AiToolDefinition tool(String name) throws Exception {
        return AiToolDefinition.function(new AiFunctionDefinition(
                name,
                "查询项目任务",
                objectMapper.readTree("{\"type\":\"object\",\"properties\":{}}")
        ));
    }

    private AiToolDefinition toolUnchecked(String name) {
        return AiToolDefinition.function(new AiFunctionDefinition(
                name,
                "查询项目任务",
                objectMapper.createObjectNode().put("type", "object")
        ));
    }
}
