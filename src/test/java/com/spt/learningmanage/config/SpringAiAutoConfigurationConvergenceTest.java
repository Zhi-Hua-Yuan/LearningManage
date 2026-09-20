package com.spt.learningmanage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.client.ai.AiChatRequestMapper;
import com.spt.learningmanage.client.ai.AiChatResponseParser;
import com.spt.learningmanage.client.ai.AiChatCommandValidator;
import com.spt.learningmanage.client.ai.AiHttpTransport;
import com.spt.learningmanage.client.ai.HutoolAiHttpTransport;
import com.spt.learningmanage.client.ai.adapter.LegacyAiChatAdapter;
import com.spt.learningmanage.client.ai.adapter.SpringAiChatAdapter;
import com.spt.learningmanage.client.ai.adapter.SpringAiStreamingModelClient;
import com.spt.learningmanage.client.ai.spi.AiChatAdapter;
import com.spt.learningmanage.service.AiStreamingModelClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAutoConfigurationConvergenceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void legacyIsTheDefaultAndDoesNotCreateSpringAiBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LegacyAiChatAdapter.class);
            assertThat(context).hasSingleBean(AiChatAdapter.class);
            assertThat(context).doesNotHaveBean(SpringAiChatAdapter.class);
            assertThat(context).doesNotHaveBean(SpringAiStreamingModelClient.class);
            assertThat(context).doesNotHaveBean(AiStreamingModelClient.class);
            assertThat(context).doesNotHaveBean(ChatModel.class);
            assertThat(context).doesNotHaveBean(ChatClient.class);
            assertThat(context).doesNotHaveBean(OpenAiApi.class);
            assertThat(context).doesNotHaveBean(EmbeddingModel.class);
            assertThat(context).doesNotHaveBean(ChatMemoryRepository.class);
            assertThat(context).doesNotHaveBean(ToolCallingManager.class);
        });
    }

    @Test
    void springAiSelectorCreatesOnlyTheManuallyGovernedChatBeans() {
        contextRunner.withPropertyValues(
                        "ai.chat.adapter=spring-ai",
                        "ai.api-key=test-key",
                        "ai.base-url=http://127.0.0.1:18080/compatible-mode/v1",
                        "ai.model=qwen-plus")
                .run(context -> {
                    assertThat(context).hasSingleBean(SpringAiChatAdapter.class);
                    assertThat(context).hasSingleBean(SpringAiStreamingModelClient.class);
                    assertThat(context).hasSingleBean(AiStreamingModelClient.class);
                    assertThat(context).hasSingleBean(AiChatAdapter.class);
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(ChatClient.class);
                    assertThat(context).hasSingleBean(OpenAiApi.class);
                    assertThat(context).doesNotHaveBean(LegacyAiChatAdapter.class);
                    assertThat(context).doesNotHaveBean(EmbeddingModel.class);
                    assertThat(context).doesNotHaveBean(ChatMemoryRepository.class);
                    assertThat(context).doesNotHaveBean(ToolCallingManager.class);
        });
    }

    @Test
    void invalidAdapterSelectorFailsContextStartup() {
        contextRunner.withPropertyValues("ai.chat.adapter=typo").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("ai.chat.adapter");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    @Import({
            AiChatAdapterConfigurationValidator.class,
            LegacyAiChatAdapter.class,
            SpringAiChatAdapter.class,
            SpringAiStreamingModelClient.class,
            SpringAiChatConfiguration.class
    })
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AiHttpTransport aiHttpTransport() {
            return new HutoolAiHttpTransport();
        }

        @Bean
        AiChatRequestMapper aiChatRequestMapper(ObjectMapper objectMapper) {
            return new AiChatRequestMapper(objectMapper);
        }

        @Bean
        AiChatResponseParser aiChatResponseParser(ObjectMapper objectMapper) {
            return new AiChatResponseParser(objectMapper);
        }

        @Bean
        AiChatCommandValidator aiChatCommandValidator(ObjectMapper objectMapper) {
            return new AiChatCommandValidator(objectMapper);
        }
    }
}
