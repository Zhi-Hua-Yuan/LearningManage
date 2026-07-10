package com.spt.learningmanage.prompt;

import com.spt.learningmanage.constant.AiPromptCodeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
    void shouldKeepTaskBreakdownJsonConstraint() {
        AiPromptTemplate template = provider.getRequired(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT);

        Assertions.assertTrue(template.systemPrompt().contains("只输出纯 JSON 数组"));
        Assertions.assertTrue(template.systemPrompt().contains("dueDate"));
    }
}
