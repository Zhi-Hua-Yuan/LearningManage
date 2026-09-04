package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionCall;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AiChatCommandValidator {

    static final int MAX_MESSAGES = 64;
    static final int MAX_TOOLS = 32;
    static final Pattern FUNCTION_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final ObjectMapper objectMapper;

    public AiChatCommandValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(AiChatCommand command) {
        require(command != null, "AI Chat 命令不能为空");
        require(hasText(command.requestedModel()), "requestedModel 不能为空");
        require(!command.messages().isEmpty() && command.messages().size() <= MAX_MESSAGES,
                "messages 数量必须为 1 至 " + MAX_MESSAGES);
        require(command.tools().size() <= MAX_TOOLS, "tools 数量不能超过 " + MAX_TOOLS);
        Set<String> functionNames = validateTools(command);
        validateMessages(command, functionNames);
        validateOptions(command);
    }

    private Set<String> validateTools(AiChatCommand command) {
        Set<String> functionNames = new HashSet<>();
        for (AiToolDefinition tool : command.tools()) {
            require(tool != null, "工具定义不能为空");
            require("function".equals(tool.type()), "当前仅支持 function 工具");
            AiFunctionDefinition function = tool.function();
            require(function != null, "function 定义不能为空");
            validateFunctionName(function.name());
            require(functionNames.add(function.name()), "工具名称不能重复: " + function.name());
            require(hasText(function.description()), "工具描述不能为空: " + function.name());
            require(function.parameters() != null && function.parameters().isObject(),
                    "工具 parameters 必须是 JSON 对象: " + function.name());
        }

        AiToolChoice choice = command.toolChoice();
        if (choice == null) {
            return functionNames;
        }
        require(choice.mode() != null, "toolChoice.mode 不能为空");
        if (choice.mode() == AiToolChoice.Mode.FUNCTION) {
            validateFunctionName(choice.functionName());
            require(functionNames.contains(choice.functionName()),
                    "强制选择的函数不在 tools 中: " + choice.functionName());
        } else {
            require(choice.functionName() == null || choice.functionName().isBlank(),
                    "AUTO/NONE 不允许指定 functionName");
        }
        return functionNames;
    }

    private void validateMessages(AiChatCommand command, Set<String> functionNames) {
        Set<String> knownToolCallIds = new HashSet<>();
        Set<String> resolvedToolCallIds = new HashSet<>();
        for (AiChatMessage message : command.messages()) {
            require(message != null, "消息不能为空");
            require(message.role() != null, "消息 role 不能为空");
            switch (message.role()) {
                case SYSTEM, USER -> {
                    require(hasText(message.content()), message.role() + " 消息 content 不能为空");
                    require(!hasText(message.toolCallId()) && message.toolCalls().isEmpty(),
                            message.role() + " 消息不能携带工具字段");
                }
                case ASSISTANT -> {
                    require(hasText(message.content()) || !message.toolCalls().isEmpty(),
                            "ASSISTANT 消息必须包含 content 或 toolCalls");
                    require(!hasText(message.toolCallId()), "ASSISTANT 消息不能携带 toolCallId");
                    for (AiToolCall toolCall : message.toolCalls()) {
                        validateToolCall(toolCall);
                        require(functionNames.contains(toolCall.function().name()),
                                "消息中的 Tool Call 必须引用已声明工具: " + toolCall.function().name());
                        require(knownToolCallIds.add(toolCall.id()), "Tool Call ID 不能重复: " + toolCall.id());
                    }
                }
                case TOOL -> {
                    require(message.content() != null, "TOOL 消息 content 必须是字符串");
                    require(hasText(message.toolCallId()), "TOOL 消息 toolCallId 不能为空");
                    require(message.toolCalls().isEmpty(), "TOOL 消息不能包含 toolCalls");
                    require(knownToolCallIds.contains(message.toolCallId()),
                            "TOOL 消息必须引用此前的 Tool Call: " + message.toolCallId());
                    require(resolvedToolCallIds.add(message.toolCallId()),
                            "同一个 Tool Call 不能重复提交结果: " + message.toolCallId());
                }
            }
        }
        require(resolvedToolCallIds.containsAll(knownToolCallIds),
                "所有历史 Tool Call 都必须包含对应的 TOOL 结果");
    }

    private void validateOptions(AiChatCommand command) {
        if (command.temperature() != null) {
            require(Double.isFinite(command.temperature())
                            && command.temperature() >= 0D
                            && command.temperature() < 2D,
                    "temperature 必须处于 [0, 2)");
        }
        if (command.maxOutputTokens() != null) {
            require(command.maxOutputTokens() > 0, "maxOutputTokens 必须为正数");
        }
    }

    void validateToolCall(AiToolCall toolCall) {
        require(toolCall != null, "Tool Call 不能为空");
        require(hasText(toolCall.id()), "Tool Call ID 不能为空");
        require("function".equals(toolCall.type()), "当前仅支持 function Tool Call");
        AiFunctionCall function = toolCall.function();
        require(function != null, "Tool Call function 不能为空");
        validateFunctionName(function.name());
        require(isJsonObject(function.arguments()), "Tool Call arguments 必须是 JSON 对象字符串");
    }

    private boolean isJsonObject(String value) {
        if (value == null) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isObject();
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private void validateFunctionName(String value) {
        require(value != null && FUNCTION_NAME_PATTERN.matcher(value).matches(),
                "函数名称必须符合 [A-Za-z0-9_-]{1,64}");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
