package com.spt.learningmanage.client.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.impl.AiModelClientImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

class RealProviderValidationIT {

    private static final int ROUND_COUNT = 3;
    private static final String PROBE_FUNCTION = "stage2_protocol_probe";
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void validateRealProviderProtocolWithoutPersistingPromptOrResponseBodies() throws Exception {
        ValidationConfiguration configuration = ValidationConfiguration.fromEnvironment();
        AiModelClient modelClient = createModelClient(configuration);
        AiToolDefinition probeTool = createProbeTool();
        List<RoundEvidence> rounds = new ArrayList<>();

        for (int round = 1; round <= ROUND_COUNT; round++) {
            rounds.add(executeRound(round, modelClient, probeTool, configuration));
        }

        ValidationReport report = ValidationReport.create(configuration, rounds);
        writeReport(configuration.reportPath(), report);

        System.out.printf(
                Locale.ROOT,
                "real-provider-validation status=PASS model=%s rounds=%d scenarios=%d totalTokens=%d latencyMs=%d%n",
                report.model(), report.roundCount(), report.scenarioCount(), report.totalTokens(), report.latencyMs()
        );
    }

    private RoundEvidence executeRound(int round,
                                       AiModelClient modelClient,
                                       AiToolDefinition probeTool,
                                       ValidationConfiguration configuration) throws Exception {
        List<ScenarioEvidence> scenarios = new ArrayList<>();

        AiChatResult textResult = timedCall(
                "text",
                () -> modelClient.chat(new AiChatCommand(
                        configuration.model(),
                        List.of(
                                AiChatMessage.system("你是协议验证助手，只需要按要求返回简短中文文本。"),
                                AiChatMessage.user("请回复：协议验证通过。")
                        ),
                        List.of(),
                        AiToolChoice.none(),
                        0D,
                        64
                )),
                scenarios,
                configuration
        );
        Assertions.assertNotNull(textResult.content(), "文本响应不能为空");
        Assertions.assertFalse(textResult.content().isBlank(), "文本响应不能为空白");
        Assertions.assertEquals("stop", textResult.finishReason(), "文本响应 finish_reason 必须为 stop");

        AiChatResult toolCallResult = timedCall(
                "forced-tool-call",
                () -> modelClient.chat(new AiChatCommand(
                        configuration.model(),
                        List.of(
                                AiChatMessage.system("你是协议验证助手，必须调用指定函数，不要自行回答。"),
                                AiChatMessage.user("请调用协议探针并传入 probeValue=wp7。")
                        ),
                        List.of(probeTool),
                        AiToolChoice.function(PROBE_FUNCTION),
                        0D,
                        128
                )),
                scenarios,
                configuration
        );
        Assertions.assertEquals("tool_calls", toolCallResult.finishReason(),
                "强制 Tool Call 的 finish_reason 必须为 tool_calls");
        Assertions.assertEquals(1, toolCallResult.toolCalls().size(), "必须且只能返回一个 Tool Call");
        AiToolCall toolCall = toolCallResult.toolCalls().get(0);
        Assertions.assertEquals(PROBE_FUNCTION, toolCall.function().name(), "模型不得调用未声明工具");
        JsonNode arguments = objectMapper.readTree(toolCall.function().arguments());
        Assertions.assertTrue(arguments.isObject(), "Tool Call arguments 必须为 JSON Object");

        AiChatResult finalResult = timedCall(
                "tool-result-round-trip",
                () -> modelClient.chat(new AiChatCommand(
                        configuration.model(),
                        List.of(
                                AiChatMessage.system("你是协议验证助手，根据工具结果返回一句简短中文结论。"),
                                AiChatMessage.user("请调用协议探针并说明结果。"),
                                AiChatMessage.assistant(null, toolCallResult.toolCalls()),
                                AiChatMessage.tool(toolCall.id(), "{\"probeValue\":\"wp7\",\"status\":\"ok\"}")
                        ),
                        List.of(probeTool),
                        AiToolChoice.none(),
                        0D,
                        96
                )),
                scenarios,
                configuration
        );
        Assertions.assertNotNull(finalResult.content(), "Tool Result 回传后必须返回文本");
        Assertions.assertFalse(finalResult.content().isBlank(), "Tool Result 回传文本不能为空白");
        Assertions.assertEquals("stop", finalResult.finishReason(),
                "Tool Result 回传后的 finish_reason 必须为 stop");
        Assertions.assertTrue(finalResult.toolCalls().isEmpty(), "tool_choice=none 时不得继续调用工具");

        return new RoundEvidence(round, "PASS", List.copyOf(scenarios));
    }

    private AiChatResult timedCall(String scenario,
                                   ThrowingSupplier<AiChatResult> call,
                                   List<ScenarioEvidence> scenarios,
                                   ValidationConfiguration configuration) throws Exception {
        long started = System.nanoTime();
        AiChatResult result = call.get();
        long latencyMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        validateCommonResult(result, configuration.model());
        scenarios.add(ScenarioEvidence.from(scenario, result, latencyMs, configuration));
        return result;
    }

    private void validateCommonResult(AiChatResult result, String requestedModel) {
        Assertions.assertEquals(requestedModel, result.requestedModel(), "requestedModel 必须保持调用值");
        Assertions.assertNotNull(result.actualModel(), "actualModel 不能为空");
        Assertions.assertFalse(result.actualModel().isBlank(), "actualModel 不能为空白");
        Assertions.assertNotNull(result.providerRequestId(), "供应商请求 ID 不能为空");
        Assertions.assertFalse(result.providerRequestId().isBlank(), "供应商请求 ID 不能为空白");
        Assertions.assertFalse(result.fallbackUsed(), "真实协议验证不得使用 fallback 掩盖失败");
        Assertions.assertEquals(0, result.retryCount(), "真实协议验证不得产生 fallback 重试");
        validateUsage(result.usage());
    }

    private void validateUsage(AiUsage usage) {
        Assertions.assertNotNull(usage, "真实供应商必须返回 Usage");
        Assertions.assertNotNull(usage.promptTokens(), "promptTokens 不能为空");
        Assertions.assertNotNull(usage.completionTokens(), "completionTokens 不能为空");
        Assertions.assertNotNull(usage.totalTokens(), "totalTokens 不能为空");
        Assertions.assertTrue(usage.promptTokens() >= 0, "promptTokens 不能为负数");
        Assertions.assertTrue(usage.completionTokens() >= 0, "completionTokens 不能为负数");
        Assertions.assertTrue(usage.totalTokens() > 0, "totalTokens 必须大于 0");
    }

    private AiToolDefinition createProbeTool() throws IOException {
        JsonNode parameters = objectMapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "probeValue": {"type": "string", "description": "固定传入 wp7"}
                  },
                  "required": ["probeValue"],
                  "additionalProperties": false
                }
                """);
        return AiToolDefinition.function(new AiFunctionDefinition(
                PROBE_FUNCTION,
                "返回固定协议探针结果，不访问任何业务数据",
                parameters
        ));
    }

    private AiModelClient createModelClient(ValidationConfiguration configuration) {
        AiProperties properties = new AiProperties();
        properties.setApiKey(configuration.apiKey());
        properties.setBaseUrl(configuration.baseUrl());
        properties.setModel(configuration.model());
        properties.setFallbackModel(null);
        properties.setConnectTimeoutMs(5_000);
        properties.setReadTimeoutMs(60_000);
        properties.getResilience().setTotalTimeoutMs(120_000);
        return new AiModelClientImpl(properties, new HutoolAiHttpTransport());
    }

    private void writeReport(Path reportPath, ValidationReport report) throws IOException {
        Path parent = reportPath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writeValue(reportPath.toFile(), report);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static BigDecimal estimateCost(AiUsage usage, ValidationConfiguration configuration) {
        BigDecimal input = configuration.inputPricePerMillion()
                .multiply(BigDecimal.valueOf(usage.promptTokens()))
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal output = configuration.outputPricePerMillion()
                .multiply(BigDecimal.valueOf(usage.completionTokens()))
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        return input.add(output).setScale(8, RoundingMode.HALF_UP);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少真实供应商验证环境变量: " + name);
        }
        return value.trim();
    }

    private static String optionalEnvironment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private record ValidationConfiguration(
            String apiKey,
            String baseUrl,
            String model,
            String priceVersion,
            String currency,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String backendSha,
            String workflowRunId,
            Path reportPath
    ) {
        private static ValidationConfiguration fromEnvironment() {
            String apiKey = requiredEnvironment("ALIYUN_API_KEY");
            if (apiKey.equals("ci-only-not-a-real-key") || apiKey.contains("please_set")) {
                throw new IllegalStateException("真实供应商验证禁止使用占位 API Key");
            }
            return new ValidationConfiguration(
                    apiKey,
                    optionalEnvironment("AI_REAL_PROVIDER_BASE_URL",
                            "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                    optionalEnvironment("AI_REAL_PROVIDER_MODEL", "qwen-plus"),
                    requiredEnvironment("AI_PRICE_VERSION"),
                    optionalEnvironment("AI_PRICE_CURRENCY", "CNY").toUpperCase(Locale.ROOT),
                    new BigDecimal(requiredEnvironment("QWEN_PLUS_INPUT_PRICE")),
                    new BigDecimal(requiredEnvironment("QWEN_PLUS_OUTPUT_PRICE")),
                    optionalEnvironment("GITHUB_SHA", "local-uncommitted"),
                    optionalEnvironment("GITHUB_RUN_ID", "local"),
                    Path.of(optionalEnvironment("AI_REAL_PROVIDER_REPORT",
                            "target/real-provider-validation.json"))
            );
        }
    }

    private record ScenarioEvidence(
            String scenario,
            String status,
            String finishReason,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            BigDecimal estimatedCost,
            String providerRequestIdHash,
            long latencyMs
    ) {
        private static ScenarioEvidence from(String scenario,
                                             AiChatResult result,
                                             long latencyMs,
                                             ValidationConfiguration configuration) {
            AiUsage usage = result.usage();
            return new ScenarioEvidence(
                    scenario,
                    "PASS",
                    result.finishReason(),
                    usage.promptTokens(),
                    usage.completionTokens(),
                    usage.totalTokens(),
                    estimateCost(usage, configuration),
                    sha256(result.providerRequestId()),
                    latencyMs
            );
        }
    }

    private record RoundEvidence(int round, String scenarioStatus, List<ScenarioEvidence> scenarios) {
    }

    private record ValidationReport(
            int schemaVersion,
            String backendSha,
            String workflowRunId,
            String model,
            int roundCount,
            int scenarioCount,
            String scenarioStatus,
            String finishReason,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            BigDecimal estimatedCost,
            String priceVersion,
            String currency,
            String providerRequestIdHash,
            long latencyMs,
            String executedAt,
            List<RoundEvidence> rounds
    ) {
        private static ValidationReport create(ValidationConfiguration configuration,
                                               List<RoundEvidence> rounds) {
            List<ScenarioEvidence> scenarios = rounds.stream()
                    .flatMap(round -> round.scenarios().stream())
                    .toList();
            int inputTokens = scenarios.stream().mapToInt(ScenarioEvidence::inputTokens).sum();
            int outputTokens = scenarios.stream().mapToInt(ScenarioEvidence::outputTokens).sum();
            int totalTokens = scenarios.stream().mapToInt(ScenarioEvidence::totalTokens).sum();
            BigDecimal cost = scenarios.stream()
                    .map(ScenarioEvidence::estimatedCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(8, RoundingMode.HALF_UP);
            long latencyMs = scenarios.stream().mapToLong(ScenarioEvidence::latencyMs).sum();
            String requestIdDigest = sha256(scenarios.stream()
                    .map(ScenarioEvidence::providerRequestIdHash)
                    .reduce("", String::concat));
            String finishReasons = scenarios.stream()
                    .map(ScenarioEvidence::finishReason)
                    .distinct()
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            return new ValidationReport(
                    1,
                    configuration.backendSha(),
                    configuration.workflowRunId(),
                    configuration.model(),
                    rounds.size(),
                    scenarios.size(),
                    "PASS",
                    finishReasons,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    cost,
                    configuration.priceVersion(),
                    configuration.currency(),
                    requestIdDigest,
                    latencyMs,
                    Instant.now().toString(),
                    List.copyOf(rounds)
            );
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
