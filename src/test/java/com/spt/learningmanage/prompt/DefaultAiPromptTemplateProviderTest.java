package com.spt.learningmanage.prompt;

import com.spt.learningmanage.constant.AiPromptCodeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class DefaultAiPromptTemplateProviderTest {

    private final DefaultAiPromptTemplateProvider provider = new DefaultAiPromptTemplateProvider();

    @Test
    void shouldReturnEveryRegisteredDefaultPrompt() {
        for (AiPromptCodeEnum promptCode : AiPromptCodeEnum.values()) {
            AiPromptTemplate template = provider.getRequired(promptCode);

            Assertions.assertEquals(promptCode.getCode(), template.code());
            Assertions.assertEquals(promptCode.getScene().getCode(), template.scene());
            Assertions.assertEquals(1, template.version());
            Assertions.assertFalse(template.systemPrompt().isBlank());
        }
    }

    @Test
    void shouldKeepTaskBreakdownFallbacksIdenticalToCandidatePrompts() throws IOException {
        AiPromptTemplate defaultTemplate = provider.getRequired(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT);
        AiPromptTemplate detailedTemplate = provider.getRequired(AiPromptCodeEnum.TASK_BREAKDOWN_DETAILED);

        Assertions.assertEquals(readCandidate("task-breakdown-default-v2.txt"), defaultTemplate.systemPrompt().strip());
        Assertions.assertEquals(readCandidate("task-breakdown-detailed-v2.txt"), detailedTemplate.systemPrompt().strip());
        Assertions.assertTrue(defaultTemplate.systemPrompt().contains("每个里程碑必须恰好输出 3 个任务"));
        Assertions.assertTrue(detailedTemplate.systemPrompt().contains("每个里程碑必须恰好输出 4 个任务"));
    }

    private String readCandidate(String fileName) throws IOException {
        return Files.readString(Path.of("evals", "stage3", "prompts", fileName), StandardCharsets.UTF_8).strip();
    }
}
