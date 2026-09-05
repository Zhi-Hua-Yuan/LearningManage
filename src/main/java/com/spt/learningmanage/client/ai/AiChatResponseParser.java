package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AiChatResponseParser {

    private static final List<String> REQUEST_ID_HEADERS = List.of(
            "x-request-id",
            "x-dashscope-request-id",
            "request-id"
    );

    private final ObjectMapper objectMapper;

    public AiChatResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiChatResult parse(AiHttpResponse response,
                              String requestedModel,
                              String actualModel,
                              int retryCount,
                              AiFailureTypeEnum fallbackReason) {
        JsonNode root = parseRoot(response.responseBody());
        JsonNode choices = root.get("choices");
        require(choices != null && choices.isArray() && !choices.isEmpty(), "AI 响应缺少 choices");
        JsonNode firstChoice = choices.get(0);
        require(firstChoice != null && firstChoice.isObject(), "AI 响应 choice 格式非法");
        JsonNode message = firstChoice.get("message");
        require(message != null && message.isObject(), "AI 响应缺少 message");
        validateAssistantRole(message.get("role"));

        String content = parseContent(message.get("content"));
        List<AiToolCall> toolCalls = parseToolCalls(message.get("tool_calls"));
        require(hasText(content) || !toolCalls.isEmpty(), "AI 响应 content 与 tool_calls 均为空");

        String finishReason = parseOptionalText(firstChoice.get("finish_reason"), "finish_reason");
        if ("tool_calls".equals(finishReason)) {
            require(!toolCalls.isEmpty(), "finish_reason=tool_calls 但响应未包含 Tool Call");
        }
        if (!toolCalls.isEmpty() && finishReason != null) {
            require("tool_calls".equals(finishReason) || "stop".equals(finishReason),
                    "finish_reason 与 Tool Calls 不一致");
        }

        AiUsage usage = parseUsage(root.get("usage"));
        String providerRequestId = firstNonBlank(
                parseOptionalText(root.get("id"), "id"),
                requestIdFromHeaders(response)
        );
        String providerModel = parseOptionalText(root.get("model"), "model");
        return new AiChatResult(
                content,
                toolCalls,
                finishReason,
                usage,
                providerRequestId,
                requestedModel,
                firstNonBlank(providerModel, actualModel),
                retryCount,
                fallbackReason != null,
                fallbackReason
        );
    }

    private JsonNode parseRoot(String responseBody) {
        require(responseBody != null && !responseBody.isBlank(), "AI 响应正文为空");
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            require(root != null && root.isObject(), "AI 响应根节点必须是 JSON 对象");
            return root;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("AI 响应不是合法 JSON", e);
        }
    }

    private void validateAssistantRole(JsonNode roleNode) {
        if (roleNode == null || roleNode.isNull()) {
            return;
        }
        require(roleNode.isTextual() && "assistant".equals(roleNode.textValue()),
                "AI 响应 message.role 必须为 assistant");
    }

    private String parseContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }
        require(contentNode.isTextual(), "当前协议不支持非字符串 content");
        return contentNode.textValue();
    }

    private List<AiToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || toolCallsNode.isNull()) {
            return List.of();
        }
        require(toolCallsNode.isArray(), "tool_calls 必须为数组");
        List<AiToolCall> toolCalls = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : toolCallsNode) {
            require(node != null && node.isObject(), "Tool Call 必须是对象");
            String id = requiredText(node.get("id"), "Tool Call ID");
            require(ids.add(id), "Tool Call ID 不能重复: " + id);
            String type = requiredText(node.get("type"), "Tool Call type");
            require("function".equals(type), "当前仅支持 function Tool Call");
            JsonNode functionNode = node.get("function");
            require(functionNode != null && functionNode.isObject(), "Tool Call function 必须是对象");
            String name = requiredText(functionNode.get("name"), "Tool Call function.name");
            require(AiChatCommandValidator.FUNCTION_NAME_PATTERN.matcher(name).matches(),
                    "Tool Call 函数名称非法");
            String arguments = requiredText(functionNode.get("arguments"), "Tool Call function.arguments");
            validateArguments(arguments);
            toolCalls.add(new AiToolCall(id, type, new AiFunctionCall(name, arguments)));
        }
        return List.copyOf(toolCalls);
    }

    private void validateArguments(String arguments) {
        try {
            JsonNode argumentsNode = objectMapper.readTree(arguments);
            require(argumentsNode != null && argumentsNode.isObject(), "Tool Call arguments 必须是 JSON 对象字符串");
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Tool Call arguments 不是合法 JSON", e);
        }
    }

    private AiUsage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull()) {
            return null;
        }
        require(usageNode.isObject(), "usage 必须是对象");
        return new AiUsage(
                parseTokenCount(usageNode.get("prompt_tokens"), "prompt_tokens"),
                parseTokenCount(usageNode.get("completion_tokens"), "completion_tokens"),
                parseTokenCount(usageNode.get("total_tokens"), "total_tokens")
        );
    }

    private Integer parseTokenCount(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        require(node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= 0,
                fieldName + " 必须是非负整数");
        return node.intValue();
    }

    private String parseOptionalText(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        require(node.isTextual(), fieldName + " 必须是字符串");
        return blankToNull(node.textValue());
    }

    private String requestIdFromHeaders(AiHttpResponse response) {
        for (String header : REQUEST_ID_HEADERS) {
            String value = response.firstHeader(header);
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String requiredText(JsonNode node, String fieldName) {
        require(node != null && node.isTextual() && hasText(node.textValue()), fieldName + " 不能为空");
        return node.textValue();
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first : blankToNull(second);
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
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
