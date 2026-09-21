package com.spt.learningmanage.client.ai.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.client.ai.spi.AiChatAdapter;
import com.spt.learningmanage.client.ai.spi.AiChatDispatchContext;
import com.spt.learningmanage.client.ai.AiChatDeadlineContext;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionCall;
import com.spt.learningmanage.model.dto.ai.chat.AiMessageRole;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spring AI 传输适配器：把 {@code ChatModel} 的输入输出翻译成
 * {@link AiChatCommand} / {@link AiChatResult}。
 *
 * <p>只做传输侧四件事：请求体构造、上游调用、响应解析、
 * HTTP 状态与网络异常到 {@code AiFailureTypeEnum} 的映射。
 * 不做治理、不注册 Tool、不重试（重试只允许发生在治理层）。</p>
 *
 * <h2>六字段映射与「缺失即阻塞」</h2>
 * <p>按 Phase 1 方案 §3.5，六类元数据必须完整映射，其中
 * {@code usage} / {@code actualModel} / {@code providerRequestId}
 * 缺失即判为失败，而不是置空放过——否则 Cost 结算、主备模型审计与
 * 上游排障会在无声中失去依据。三者的「缺失」在 Spring AI 里都<b>不是 null</b>，
 * 必须按各自的哨兵值识别：</p>
 * <ul>
 *   <li>{@code usage}：上游未回 usage 时框架给的是 {@link EmptyUsage}
 *       （tokens 全为 0），与「真实的 0 token」表象相同，
 *       只能靠类型区分，不能靠数值区分；</li>
 *   <li>{@code actualModel} / {@code providerRequestId}：框架用空字符串兜底
 *       （见 {@code OpenAiChatModel#from}），所以要按空白判定而非判空。</li>
 * </ul>
 *
 * <p>已知能力差异（非阻塞，已在 PR 中记录）：providerRequestId 在 Spring AI
 * 路径上只能取到响应正文的 id，取不到 {@code x-request-id} 等响应头回退；
 * 而正文 id 是本工程的主取数路径（legacy 也是先取正文 id），
 * 因此不影响既有上游的排障能力。</p>
 */
@Component
@ConditionalOnProperty(name = "ai.chat.adapter", havingValue = "spring-ai")
public class SpringAiChatAdapter implements AiChatAdapter {

    private final ChatModel chatModel;

    private final ObjectMapper objectMapper;

    public SpringAiChatAdapter(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResult chat(AiChatCommand command, AiChatDispatchContext context) {
        String model = command.requestedModel();
        int retryCount = context.retryCount();

        Prompt prompt;
        try {
            prompt = buildPrompt(command);
        } catch (RuntimeException e) {
            throw invocationException(
                    AiFailureTypeEnum.INTERNAL_ERROR,
                    model,
                    retryCount,
                    "AI 请求构造失败，请联系管理员",
                    "构造 AI 上游请求失败: model=" + model,
                    e,
                    null
            );
        }

        ChatResponse response;
        try {
            try (AiChatDeadlineContext.Scope ignored = AiChatDeadlineContext.open(context.deadlineNanos())) {
                response = chatModel.call(prompt);
            }
        } catch (AiUpstreamHttpException e) {
            AiFailureTypeEnum failureType = resolveHttpFailureType(e.getStatusCode());
            throw invocationException(
                    failureType,
                    model,
                    retryCount,
                    safeMessageFor(failureType),
                    "AI 上游响应异常: model=" + model + ", status=" + e.getStatusCode(),
                    null,
                    e.getStatusCode()
            );
        } catch (Exception e) {
            if (containsResponseDecodingFailure(e)) {
                throw invocationException(
                        AiFailureTypeEnum.INVALID_RESPONSE,
                        model,
                        retryCount,
                        "AI 返回结果格式异常，请重试",
                        "解码 AI 上游响应失败: model=" + model,
                        e,
                        null
                );
            }
            if (containsSocketTimeout(e)) {
                throw invocationException(
                        AiFailureTypeEnum.TIMEOUT,
                        model,
                        retryCount,
                        "AI 服务响应超时，请稍后重试",
                        "AI 请求超时: model=" + model,
                        e,
                        null
                );
            }
            throw invocationException(
                    AiFailureTypeEnum.NETWORK_ERROR,
                    model,
                    retryCount,
                    "AI 服务暂时不可用，请稍后重试",
                    "AI 网络请求失败: model=" + model + ", cause=" + e.getClass().getSimpleName(),
                    e,
                    null
            );
        }

        try {
            return mapResponse(response, context, model);
        } catch (AiInvocationException e) {
            throw e;
        } catch (Exception e) {
            // 把「哪一类元数据缺失」带进内部消息：面向用户的 safeMessage 保持通用，
            // 而这条只落服务端日志，是排障时唯一能区分六字段缺失原因的地方。
            throw invocationException(
                    AiFailureTypeEnum.INVALID_RESPONSE,
                    model,
                    retryCount,
                    "AI 返回结果格式异常，请重试",
                    "解析 AI 上游响应失败: model=" + model + ", cause=" + e.getMessage(),
                    e,
                    null
            );
        }
    }

    // ------------------------------------------------------------------
    // 请求侧：AiChatCommand -> Prompt
    // ------------------------------------------------------------------

    private Prompt buildPrompt(AiChatCommand command) {
        List<Message> messages = new ArrayList<>(command.messages().size());
        Map<String, String> toolNamesById = new HashMap<>();
        for (AiChatMessage message : command.messages()) {
            if (message.role() == AiMessageRole.ASSISTANT) {
                for (AiToolCall toolCall : message.toolCalls()) {
                    toolNamesById.put(toolCall.id(), toolCall.function().name());
                }
            }
            messages.add(toMessage(message, toolNamesById));
        }
        return new Prompt(messages, toOptions(command));
    }

    private Message toMessage(AiChatMessage message, Map<String, String> toolNamesById) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(blankToEmpty(message.content()));
            case USER -> new UserMessage(blankToEmpty(message.content()));
            case ASSISTANT -> toAssistantMessage(message);
            case TOOL -> toToolResponseMessage(message, toolNamesById);
        };
    }

    private AssistantMessage toAssistantMessage(AiChatMessage message) {
        List<AssistantMessage.ToolCall> toolCalls = message.toolCalls().stream()
                .map(toolCall -> new AssistantMessage.ToolCall(
                        toolCall.id(),
                        toolCall.type(),
                        toolCall.function().name(),
                        toolCall.function().arguments()
                ))
                .toList();
        return AssistantMessage.builder()
                .content(message.content())
                .toolCalls(toolCalls)
                .build();
    }

    private ToolResponseMessage toToolResponseMessage(AiChatMessage message, Map<String, String> toolNamesById) {
        String toolName = toolNamesById.get(message.toolCallId());
        if (toolName == null) {
            // 命令校验器保证 tool 消息只引用此前已声明的 Tool Call，
            // 走到这里说明命令与历史不一致，属于内部错误而非上游问题。
            throw new IllegalArgumentException("tool 消息引用了未声明的 Tool Call: " + message.toolCallId());
        }
        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse(message.toolCallId(), toolName, blankToEmpty(message.content()));
        return ToolResponseMessage.builder().responses(List.of(toolResponse)).build();
    }

    private OpenAiChatOptions toOptions(AiChatCommand command) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(command.requestedModel());
        if (command.temperature() != null) {
            builder.temperature(command.temperature());
        }
        if (command.maxOutputTokens() != null) {
            builder.maxTokens(command.maxOutputTokens());
        }
        if (!command.tools().isEmpty()) {
            // Request-scoped definition carriers only. The application Agent
            // manager executes calls after this adapter returns.
            builder.toolCallbacks(toToolCallbacks(command.tools()));
            // 与 legacy 一致：本工程不支持并行工具调用。
            builder.parallelToolCalls(false);
            // 工具由 agent 层执行，框架不得自行执行。
            builder.internalToolExecutionEnabled(false);
            builder.toolChoice(toToolChoice(command.toolChoice()));
        }
        return builder.build();
    }

    private List<ToolCallback> toToolCallbacks(List<AiToolDefinition> tools) {
        return tools.stream().map(this::toToolCallback).toList();
    }

    private ToolCallback toToolCallback(AiToolDefinition tool) {
        try {
            ToolDefinition definition = DefaultToolDefinition.builder()
                    .name(tool.function().name())
                    .description(tool.function().description())
                    .inputSchema(objectMapper.writeValueAsString(tool.function().parameters()))
                    .build();
            return new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return definition;
                }

                @Override
                public String call(String arguments) {
                    // A callback reaching here means framework-level
                    // execution was enabled accidentally. Fail closed rather
                    // than executing a business Tool without Run context.
                    throw new IllegalStateException("LearningManage Tool 必须由应用级 Manager 执行");
                }
            };
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tool Schema 序列化失败: " + tool.function().name(), exception);
        }
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

    // ------------------------------------------------------------------
    // 响应侧：ChatResponse -> AiChatResult（六字段）
    // ------------------------------------------------------------------

    private AiChatResult mapResponse(ChatResponse response, AiChatDispatchContext context, String model) {
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new IllegalArgumentException("AI 响应缺少生成结果");
        }
        AssistantMessage output = generation.getOutput();
        String content = output.getText();
        List<AiToolCall> toolCalls = toToolCalls(output.getToolCalls());
        if ((content == null || content.isBlank()) && toolCalls.isEmpty()) {
            throw new IllegalArgumentException("AI 响应 content 与 tool_calls 均为空");
        }

        String finishReason = resolveFinishReason(generation);
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata == null) {
            throw new IllegalArgumentException("AI 响应缺少元数据");
        }
        AiUsage usage = resolveUsage(metadata);
        String actualModel = requireMetadataText(metadata.getModel(), "actualModel");
        String providerRequestId = requireMetadataText(metadata.getId(), "providerRequestId");

        return new AiChatResult(
                content,
                toolCalls,
                finishReason,
                usage,
                providerRequestId,
                context.requestedModel(),
                actualModel,
                context.retryCount(),
                context.fallbackReason() != null,
                context.fallbackReason()
        );
    }

    private List<AiToolCall> toToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        List<AiToolCall> mapped = new ArrayList<>(toolCalls.size());
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            if (toolCall.id() == null || toolCall.id().isBlank()) {
                throw new IllegalArgumentException("Tool Call 缺少 id");
            }
            if (toolCall.name() == null || toolCall.name().isBlank()) {
                throw new IllegalArgumentException("Tool Call 缺少函数名");
            }
            mapped.add(new AiToolCall(
                    toolCall.id(),
                    toolCall.type(),
                    new AiFunctionCall(toolCall.name(), toolCall.arguments())
            ));
        }
        return mapped;
    }

    /**
     * finishReason 补齐成线协议形态。
     *
     * <p>必须做这步转换：Spring AI 的 {@code OpenAiChatModel#getFinishReasonJson}
     * 返回的是枚举常量名（{@code STOP}、{@code TOOL_CALLS}），而 legacy 侧透传的是
     * 上游原值（{@code stop}、{@code tool_calls}）。这个值会落进
     * {@code ai_call_log.finish_reason}，并被 agent 侧按 {@code "tool_calls"} 判断，
     * 若两套适配器给出不同大小写，切换适配器就会改变持久化内容与分支行为。</p>
     */
    private String resolveFinishReason(Generation generation) {
        ChatGenerationMetadata generationMetadata = generation.getMetadata();
        String finishReason = generationMetadata == null ? null : generationMetadata.getFinishReason();
        if (finishReason == null || finishReason.isBlank()) {
            throw new IllegalArgumentException("AI 响应缺少 finish_reason");
        }
        return finishReason.toLowerCase(Locale.ROOT);
    }

    /**
     * usage 缺失即阻塞。注意上游未回 usage 时框架给的是 {@link EmptyUsage}
     * （tokens 全 0）而非 null，只能用类型识别。
     */
    private AiUsage resolveUsage(ChatResponseMetadata metadata) {
        Usage usage = metadata.getUsage();
        if (usage == null || usage instanceof EmptyUsage) {
            throw new IllegalArgumentException("AI 响应缺少 usage，无法进行用量与成本核算");
        }
        if (usage.getPromptTokens() == null || usage.getCompletionTokens() == null) {
            throw new IllegalArgumentException("AI 响应 usage 缺少 token 明细");
        }
        return new AiUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * actualModel / providerRequestId 缺失即阻塞。框架用空字符串兜底，
     * 因此必须按空白判定。
     */
    private String requireMetadataText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI 响应缺少 " + fieldName);
        }
        return value;
    }

    // ------------------------------------------------------------------
    // 失败分类：与 legacy 保持同一套规则
    // ------------------------------------------------------------------

    private AiFailureTypeEnum resolveHttpFailureType(int statusCode) {
        if (statusCode == 408 || statusCode == 504) {
            return AiFailureTypeEnum.TIMEOUT;
        }
        if (statusCode == 429) {
            return AiFailureTypeEnum.RATE_LIMITED;
        }
        if (statusCode >= 500) {
            return AiFailureTypeEnum.UPSTREAM_SERVER_ERROR;
        }
        return AiFailureTypeEnum.UPSTREAM_REJECTED;
    }

    private String safeMessageFor(AiFailureTypeEnum failureType) {
        return switch (failureType) {
            case TIMEOUT -> "AI 服务响应超时，请稍后重试";
            case RATE_LIMITED -> "AI 服务当前请求较多，请稍后重试";
            case UPSTREAM_REJECTED -> "AI 服务请求被拒绝，请联系管理员";
            default -> "AI 服务暂时不可用，请稍后重试";
        };
    }

    private boolean containsSocketTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            if (current instanceof ConnectException
                    && current.getMessage() != null
                    && current.getMessage().toLowerCase().contains("timed out")) {
                return true;
            }
            if (current instanceof ResourceAccessException
                    && current.getMessage() != null
                    && current.getMessage().toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsResponseDecodingFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpMessageConversionException
                    || (current instanceof RestClientException
                    && current.getCause() instanceof HttpMessageConversionException)
                    || current instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private AiInvocationException invocationException(AiFailureTypeEnum failureType,
                                                      String model,
                                                      int retryCount,
                                                      String safeMessage,
                                                      String internalMessage,
                                                      Throwable cause,
                                                      Integer httpStatusCode) {
        return new AiInvocationException(
                failureType,
                model,
                retryCount,
                safeMessage,
                internalMessage,
                cause,
                httpStatusCode
        );
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
