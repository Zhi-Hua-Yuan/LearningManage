package com.spt.learningmanage.client.ai.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.client.ai.AiChatCommandValidator;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionCall;
import com.spt.learningmanage.model.dto.ai.chat.AiStreamingChunk;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Spring AI streaming transport seam; intentionally unused by business code in Phase 1. */
@Component
@ConditionalOnProperty(name = "ai.chat.adapter", havingValue = "spring-ai")
public class SpringAiStreamingModelClient implements com.spt.learningmanage.service.AiStreamingModelClient {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final AiChatCommandValidator commandValidator;

    public SpringAiStreamingModelClient(ChatModel chatModel,
                                        ObjectMapper objectMapper,
                                        AiChatCommandValidator commandValidator) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.commandValidator = commandValidator;
    }

    @Override
    public Flux<AiStreamingChunk> stream(AiChatCommand command) {
        commandValidator.validate(command);
        Prompt prompt = new Prompt(toMessages(command), toOptions(command));
        // No retry/repeat operator is allowed here. Reactor cancellation naturally
        // cancels the underlying ChatModel subscription.
        return chatModel.stream(prompt).map(this::mapChunk);
    }

    private List<Message> toMessages(AiChatCommand command) {
        Map<String, String> toolNamesById = command.messages().stream()
                .filter(message -> message.role() == com.spt.learningmanage.model.dto.ai.chat.AiMessageRole.ASSISTANT)
                .flatMap(message -> message.toolCalls().stream())
                .collect(java.util.stream.Collectors.toMap(
                        AiToolCall::id,
                        call -> call.function().name(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Message> messages = new ArrayList<>(command.messages().size());
        for (AiChatMessage message : command.messages()) {
            switch (message.role()) {
                case SYSTEM -> messages.add(new SystemMessage(blankToEmpty(message.content())));
                case USER -> messages.add(new UserMessage(blankToEmpty(message.content())));
                case ASSISTANT -> messages.add(AssistantMessage.builder()
                        .content(blankToEmpty(message.content()))
                        .toolCalls(message.toolCalls().stream().map(call -> new AssistantMessage.ToolCall(
                                call.id(), call.type(), call.function().name(), call.function().arguments())).toList())
                        .build());
                case TOOL -> {
                    String toolName = toolNamesById.get(message.toolCallId());
                    if (toolName == null) {
                        throw new IllegalArgumentException("tool 消息引用了未声明的 Tool Call: " + message.toolCallId());
                    }
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(new ToolResponseMessage.ToolResponse(
                                    message.toolCallId(), toolName, blankToEmpty(message.content()))))
                            .build());
                }
            }
        }
        return messages;
    }

    private OpenAiChatOptions toOptions(AiChatCommand command) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(command.requestedModel());
        if (command.temperature() != null) {
            builder.temperature(command.temperature());
        }
        if (command.maxOutputTokens() != null) {
            builder.maxTokens(command.maxOutputTokens());
        }
        if (!command.tools().isEmpty()) {
            builder.tools(command.tools().stream().map(tool -> {
                Map<String, Object> parameters = objectMapper.convertValue(
                        tool.function().parameters(), new TypeReference<LinkedHashMap<String, Object>>() { });
                OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(
                        tool.function().description(), tool.function().name(), parameters, null);
                return new OpenAiApi.FunctionTool(OpenAiApi.FunctionTool.Type.FUNCTION, function);
            }).toList());
            builder.parallelToolCalls(false);
            builder.internalToolExecutionEnabled(false);
            builder.toolChoice(toToolChoice(command.toolChoice()));
        }
        return builder.build();
    }

    private Object toToolChoice(AiToolChoice toolChoice) {
        if (toolChoice == null || toolChoice.mode() == AiToolChoice.Mode.AUTO) {
            return "auto";
        }
        return switch (toolChoice.mode()) {
            case NONE -> "none";
            case FUNCTION -> OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.function(toolChoice.functionName());
            case AUTO -> "auto";
        };
    }

    private AiStreamingChunk mapChunk(ChatResponse response) {
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new IllegalStateException("AI streaming response 缺少生成结果");
        }
        AssistantMessage output = generation.getOutput();
        String content = output.getText();
        List<AiToolCall> toolCalls = output.getToolCalls() == null ? List.of() : output.getToolCalls().stream()
                .map(call -> new AiToolCall(call.id(), call.type(),
                        new AiFunctionCall(call.name(), call.arguments())))
                .toList();
        ChatGenerationMetadata generationMetadata = generation.getMetadata();
        String finishReason = generationMetadata == null ? null : generationMetadata.getFinishReason();
        boolean terminal = finishReason != null && !finishReason.isBlank();
        ChatResponseMetadata metadata = response.getMetadata();
        if (!terminal) {
            return new AiStreamingChunk(content, toolCalls, null, null, null, null, false);
        }
        if (metadata == null) {
            throw new IllegalStateException("AI streaming terminal response 缺少元数据");
        }
        Usage usage = metadata.getUsage();
        if (usage == null || usage instanceof EmptyUsage
                || usage.getPromptTokens() == null || usage.getCompletionTokens() == null) {
            throw new IllegalStateException("AI streaming terminal response 缺少 usage");
        }
        if (metadata.getModel() == null || metadata.getModel().isBlank()
                || metadata.getId() == null || metadata.getId().isBlank()) {
            throw new IllegalStateException("AI streaming terminal response 缺少模型或 provider request ID");
        }
        return new AiStreamingChunk(
                content,
                toolCalls,
                finishReason.toLowerCase(Locale.ROOT),
                new AiUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens()),
                metadata.getModel(), metadata.getId(), true);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
