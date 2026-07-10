package com.spt.learningmanage.prompt;

import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.mapper.PromptTemplateMapper;
import com.spt.learningmanage.model.entity.PromptTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PromptTemplateResolverTest {

    private final PromptTemplateMapper promptTemplateMapper = Mockito.mock(PromptTemplateMapper.class);

    private final PromptTemplateResolver resolver = new PromptTemplateResolver(
            promptTemplateMapper,
            new DefaultAiPromptTemplateProvider()
    );

    @Test
    void resolve_shouldUseEnabledDatabaseTemplate() {
        PromptTemplate databaseTemplate = new PromptTemplate();
        databaseTemplate.setId(101L);
        databaseTemplate.setTemplateCode(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT.getCode());
        databaseTemplate.setScene(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT.getScene().getCode());
        databaseTemplate.setVersion(2);
        databaseTemplate.setEnabled(1);
        databaseTemplate.setTemplateContent("数据库模板内容");
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(databaseTemplate));

        AiPromptTemplate result = resolver.resolve(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT);

        Assertions.assertEquals(101L, result.templateId());
        Assertions.assertEquals(2, result.version());
        Assertions.assertEquals(AiPromptSourceEnum.DATABASE, result.source());
        Assertions.assertEquals("数据库模板内容", result.systemPrompt());
    }

    @Test
    void resolve_shouldFallBackToBuiltinWhenNoEnabledTemplate() {
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of());

        AiPromptTemplate result = resolver.resolve(AiPromptCodeEnum.WEEKLY_POLISH_DEFAULT);

        Assertions.assertNull(result.templateId());
        Assertions.assertEquals(1, result.version());
        Assertions.assertEquals(AiPromptSourceEnum.BUILTIN, result.source());
        Assertions.assertFalse(result.systemPrompt().isBlank());
    }

    @Test
    void resolve_shouldFallBackToBuiltinWhenEnabledTemplateSceneIsInvalid() {
        PromptTemplate invalidTemplate = new PromptTemplate();
        invalidTemplate.setId(102L);
        invalidTemplate.setTemplateCode(AiPromptCodeEnum.TODAY_ORDER_DEFAULT.getCode());
        invalidTemplate.setScene("weekly-polish");
        invalidTemplate.setVersion(1);
        invalidTemplate.setEnabled(1);
        invalidTemplate.setTemplateContent("不应使用的模板");
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(invalidTemplate));

        AiPromptTemplate result = resolver.resolve(AiPromptCodeEnum.TODAY_ORDER_DEFAULT);

        Assertions.assertEquals(AiPromptSourceEnum.BUILTIN, result.source());
        Assertions.assertEquals(AiPromptCodeEnum.TODAY_ORDER_DEFAULT.getCode(), result.code());
    }
}
