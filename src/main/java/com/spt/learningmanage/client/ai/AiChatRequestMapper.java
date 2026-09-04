package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import org.springframework.stereotype.Component;

@Component
public class AiChatRequestMapper {

    private final ObjectMapper objectMapper;

    public AiChatRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(AiChatCommand command) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", command.requestedModel());
        root.set("messages", mapMessages(command));
        if (!command.tools().isEmpty()) {
            root.set("tools", mapTools(command));
            root.put("parallel_tool_calls", false);
        }
        mapToolChoice(root, command);
        if (command.temperature() != null) {
            root.put("temperature", command.temperature());
        }
        if (command.maxOutputTokens() != null) {
            root.put("max_tokens", command.maxOutputTokens());
        }
        root.put("stream", false);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法序列化 AI Chat 请求", e);
        }
    }

    private ArrayNode mapMessages(AiChatCommand command) {
        ArrayNode messages = objectMapper.createArrayNode();
        for (AiChatMessage message : command.messages()) {
            ObjectNode node = messages.addObject();
            node.put("role", message.role().getWireName());
            if (message.content() == null) {
                node.putNull("content");
            } else {
                node.put("content", message.content());
            }
            if (message.toolCallId() != null) {
                node.put("tool_call_id", message.toolCallId());
            }
            if (!message.toolCalls().isEmpty()) {
                node.set("tool_calls", mapToolCalls(message));
            }
        }
        return messages;
    }

    private ArrayNode mapToolCalls(AiChatMessage message) {
        ArrayNode toolCalls = objectMapper.createArrayNode();
        for (int index = 0; index < message.toolCalls().size(); index++) {
            AiToolCall toolCall = message.toolCalls().get(index);
            ObjectNode node = toolCalls.addObject();
            node.put("index", index);
            node.put("id", toolCall.id());
            node.put("type", toolCall.type());
            ObjectNode function = node.putObject("function");
            function.put("name", toolCall.function().name());
            function.put("arguments", toolCall.function().arguments());
        }
        return toolCalls;
    }

    private ArrayNode mapTools(AiChatCommand command) {
        ArrayNode tools = objectMapper.createArrayNode();
        for (AiToolDefinition tool : command.tools()) {
            ObjectNode node = tools.addObject();
            node.put("type", tool.type());
            ObjectNode function = node.putObject("function");
            function.put("name", tool.function().name());
            function.put("description", tool.function().description());
            function.set("parameters", tool.function().parameters());
        }
        return tools;
    }

    private void mapToolChoice(ObjectNode root, AiChatCommand command) {
        AiToolChoice choice = command.toolChoice();
        if (choice == null) {
            if (!command.tools().isEmpty()) {
                root.put("tool_choice", "auto");
            }
            return;
        }
        switch (choice.mode()) {
            case AUTO -> root.put("tool_choice", "auto");
            case NONE -> root.put("tool_choice", "none");
            case FUNCTION -> {
                ObjectNode toolChoice = root.putObject("tool_choice");
                toolChoice.put("type", "function");
                toolChoice.putObject("function").put("name", choice.functionName());
            }
        }
    }
}
