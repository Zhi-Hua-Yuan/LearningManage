package com.spt.learningmanage.config;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.client.ai.AiChatDeadlineContext;
import com.spt.learningmanage.client.ai.AiTimeoutPolicy;
import com.spt.learningmanage.client.ai.adapter.AiUpstreamErrorHandler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Spring AI chat 传输层的装配，仅在 {@code ai.chat.adapter=spring-ai} 时生效。
 *
 * <p>这里手工装配而不是依赖 starter 的自动配置，原因是自动配置在本工程里是
 * 「默认开启」的：{@code spring.ai.model.chat}、{@code spring.ai.model.embedding}
 * 等六个开关的 {@code matchIfMissing} 都是 {@code true}，只要 starter 在
 * classpath 上且没被显式关掉，就会凭空造出 chat / embedding / image / audio /
 * moderation 六类模型 Bean。其中 embedding 尤其危险——本工程的向量化有自己的
 * DashScope 通道，多出一个 EmbeddingModel 会悄悄改变 RAG 行为面。
 * 因此 application.yml 把六个开关全部钉成 {@code none}，
 * 再由本类按需提供唯一的 ChatModel。参见
 * {@code SpringAiAutoConfigurationConvergenceTest} 的收敛断言。</p>
 *
 * <p>三个 Bean 都带 {@code @ConditionalOnMissingBean}，便于测试替换，
 * 也避免与将来可能启用的自动配置互相打架。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.chat.adapter", havingValue = "spring-ai")
public class SpringAiChatConfiguration {

    /**
     * 与 legacy 适配器的拼接口径保持一致：{@code base-url} + {@code "/chat/completions"}。
     *
     * <p>Spring AI 的默认 {@code completions-path} 是 {@code /v1/chat/completions}，
     * 直接沿用会把 {@code …/compatible-mode/v1} 拼成 {@code …/v1/v1/…}。
     * 这里显式覆盖成 {@code /chat/completions}，使两个适配器在
     * 同一份 {@code ai.base-url} 下请求到**完全相同的 URL**，
     * 而不是靠「base-url 正好去掉 /v1」这种隐式巧合。</p>
     */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /**
     * 不重试：Spring AI 的默认 {@code RetryTemplate} 是
     * {@code maxAttempts(10)} + 2000ms 起、乘数 5、上限 180s 的指数退避，
     * 且会对 5xx（{@code TransientAiException}）与网络异常
     * （{@code ResourceAccessException}）重试。
     *
     * <p>本工程的重试、主备模型切换、熔断与用量统计都在治理层
     * （AI 调用的唯一受治理实现）完成，并依赖「一次逻辑调用 = 可数的上游请求」。
     * 框架层再插一层 10 倍重试会让治理层的次数、耗时与计价全部失真，
     * 还可能直接击穿 {@code ai.resilience.total-timeout-ms}。
     * 所以这里把最大尝试次数压到 1，把重试权收回治理层。</p>
     */
    private static final int FRAMEWORK_MAX_ATTEMPTS = 1;

    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi springAiOpenAiApi(AiProperties aiProperties) {
        DeadlineAwareRequestFactory requestFactory = new DeadlineAwareRequestFactory(
                AiTimeoutPolicy.connectTimeoutMs(aiProperties),
                AiTimeoutPolicy.readTimeoutMs(aiProperties));

        return OpenAiApi.builder()
                .baseUrl(StrUtil.removeSuffix(aiProperties.getBaseUrl().trim(), "/"))
                .completionsPath(CHAT_COMPLETIONS_PATH)
                .apiKey(StrUtil.nullToEmpty(aiProperties.getApiKey()).trim())
                // OpenAiApi writes the request through a streaming message
                // converter. Buffering makes the request length explicit so
                // the OpenAI-compatible CI stub (and strict proxies) receive
                // a normal fixed-length JSON request instead of an empty body
                // when no Content-Length was produced by the converter.
                .restClientBuilder(RestClient.builder().requestFactory(
                        new BufferingClientHttpRequestFactory(requestFactory)))
                .responseErrorHandler(new AiUpstreamErrorHandler())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatModel springAiChatModel(OpenAiApi openAiApi, AiProperties aiProperties) {
        // 默认选项只提供兜底模型名；每次调用的实际模型由适配器按命令下发，
        // 主备切换的决策权留在治理层。
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(aiProperties.getModel())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .retryTemplate(RetryTemplate.builder().maxAttempts(FRAMEWORK_MAX_ATTEMPTS).build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatClient springAiChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * Applies the governance deadline at request creation time. A single
     * ChatModel instance is shared by the application, so the per-attempt
     * deadline is carried by the synchronous call's thread-local scope rather
     * than by mutable model configuration.
     */
    static final class DeadlineAwareRequestFactory extends SimpleClientHttpRequestFactory {

        private final int configuredConnectTimeoutMs;
        private final int configuredReadTimeoutMs;

        DeadlineAwareRequestFactory(int configuredConnectTimeoutMs, int configuredReadTimeoutMs) {
            this.configuredConnectTimeoutMs = configuredConnectTimeoutMs;
            this.configuredReadTimeoutMs = configuredReadTimeoutMs;
            setConnectTimeout(Duration.ofMillis(configuredConnectTimeoutMs));
            setReadTimeout(Duration.ofMillis(configuredReadTimeoutMs));
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            Long deadlineNanos = AiChatDeadlineContext.currentDeadlineNanos();
            if (deadlineNanos == null) {
                return;
            }
            int remainingMs = AiTimeoutPolicy.remainingTimeoutMs(deadlineNanos);
            connection.setConnectTimeout(Math.min(configuredConnectTimeoutMs, remainingMs));
            connection.setReadTimeout(Math.min(configuredReadTimeoutMs, remainingMs));
        }
    }
}
