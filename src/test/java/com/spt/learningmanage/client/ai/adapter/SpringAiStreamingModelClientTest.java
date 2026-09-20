package com.spt.learningmanage.client.ai.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.client.ai.AiChatCommandValidator;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiStreamingChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiStreamingModelClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stream_shouldExposeDeltasAndFinalMetadataOnlyOnTerminalChunk() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse intermediate = new ChatResponse(List.of(new Generation(new AssistantMessage("part"))));
        ChatResponse terminal = new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage("done"),
                        ChatGenerationMetadata.builder().finishReason("STOP").build())),
                ChatResponseMetadata.builder()
                        .id("provider-stream-id")
                        .model("qwen-plus-provider")
                        .usage(new DefaultUsage(12, 8, 20))
                        .build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(intermediate, terminal));

        List<AiStreamingChunk> chunks = client(chatModel).stream(command()).collectList().block(Duration.ofSeconds(2));

        assertEquals(2, chunks.size());
        AiStreamingChunk first = chunks.get(0);
        assertEquals("part", first.contentDelta());
        assertFalse(first.terminal());
        assertEquals(null, first.finishReason());
        assertEquals(null, first.usage());
        assertEquals(null, first.actualModel());
        assertEquals(null, first.providerRequestId());

        AiStreamingChunk last = chunks.get(1);
        assertEquals("done", last.contentDelta());
        assertTrue(last.terminal());
        assertEquals("stop", last.finishReason());
        assertEquals(12, last.usage().promptTokens());
        assertEquals(8, last.usage().completionTokens());
        assertEquals(20, last.usage().totalTokens());
        assertEquals("qwen-plus-provider", last.actualModel());
        assertEquals("provider-stream-id", last.providerRequestId());
    }

    @Test
    void stream_shouldFailWhenTerminalMetadataIsIncomplete() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse terminalWithoutId = new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage("done"),
                        ChatGenerationMetadata.builder().finishReason("stop").build())),
                ChatResponseMetadata.builder()
                        .model("qwen-plus-provider")
                        .usage(new DefaultUsage(1, 1, 2))
                        .build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(terminalWithoutId));

        assertThrows(IllegalStateException.class,
                () -> client(chatModel).stream(command()).collectList().block(Duration.ofSeconds(2)));
    }

    @Test
    void stream_shouldPropagateCancellationToUnderlyingModelFlux() {
        ChatModel chatModel = mock(ChatModel.class);
        AtomicBoolean cancelled = new AtomicBoolean();
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.create(sink -> sink.onCancel(() -> cancelled.set(true))));

        var subscription = client(chatModel).stream(command()).subscribe();
        subscription.dispose();

        assertTrue(cancelled.get());
    }

    private SpringAiStreamingModelClient client(ChatModel chatModel) {
        return new SpringAiStreamingModelClient(chatModel, objectMapper,
                new AiChatCommandValidator(objectMapper));
    }

    private AiChatCommand command() {
        return new AiChatCommand("qwen-plus", List.of(AiChatMessage.user("stream")),
                List.of(), null, 0.0D, 128);
    }
}
