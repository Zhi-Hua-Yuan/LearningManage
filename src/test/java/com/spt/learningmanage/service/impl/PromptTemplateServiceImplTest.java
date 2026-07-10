package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.PromptTemplateMapper;
import com.spt.learningmanage.model.dto.ai.PromptTemplateCreateVersionRequest;
import com.spt.learningmanage.model.entity.PromptTemplate;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptTemplateServiceImplTest {

    private PromptTemplateMapper promptTemplateMapper;

    private PromptTemplateServiceImpl promptTemplateService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PromptTemplate.class
        );
    }

    @BeforeEach
    void setUp() {
        promptTemplateMapper = Mockito.mock(PromptTemplateMapper.class);
        promptTemplateService = new PromptTemplateServiceImpl(promptTemplateMapper);
    }

    @Test
    void createVersion_shouldIncrementLatestVersionAndDeriveScene() {
        PromptTemplateCreateVersionRequest request = buildCreateRequest();
        when(promptTemplateMapper.selectLatestVersionForUpdate("task-breakdown.default"))
                .thenReturn(4);
        when(promptTemplateMapper.insert(any(PromptTemplate.class))).thenAnswer(invocation -> {
            PromptTemplate template = invocation.getArgument(0);
            template.setId(105L);
            return 1;
        });

        Long id = promptTemplateService.createVersion(request);

        Assertions.assertEquals(105L, id);
        ArgumentCaptor<PromptTemplate> captor = ArgumentCaptor.forClass(PromptTemplate.class);
        verify(promptTemplateMapper).insert(captor.capture());
        PromptTemplate inserted = captor.getValue();
        Assertions.assertEquals("task-breakdown", inserted.getScene());
        Assertions.assertEquals(5, inserted.getVersion());
        Assertions.assertEquals(0, inserted.getEnabled());
        Assertions.assertEquals(0, inserted.getIsDelete());
    }

    @Test
    void createVersion_shouldRejectUnsupportedPromptCode() {
        PromptTemplateCreateVersionRequest request = buildCreateRequest();
        request.setTemplateCode("unknown.prompt");

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> promptTemplateService.createVersion(request)
        );

        Assertions.assertEquals(ErrorCode.PARAMS_ERROR, exception.getErrorCode());
        verify(promptTemplateMapper, never()).insert(any(PromptTemplate.class));
    }

    @Test
    void activate_shouldDisableOldVersionAndEnableTargetVersion() {
        PromptTemplate oldVersion = buildTemplate(101L, 1, 1);
        PromptTemplate targetVersion = buildTemplate(102L, 2, 0);
        when(promptTemplateMapper.selectById(102L)).thenReturn(targetVersion);
        when(promptTemplateMapper.selectVersionsForUpdate("task-breakdown.default"))
                .thenReturn(List.of(oldVersion, targetVersion));
        when(promptTemplateMapper.update(Mockito.isNull(), any())).thenReturn(1);

        promptTemplateService.activate(102L);

        verify(promptTemplateMapper, times(2)).update(Mockito.isNull(), any());
    }

    @Test
    void activate_shouldBeIdempotentWhenTargetIsAlreadyEnabled() {
        PromptTemplate targetVersion = buildTemplate(101L, 1, 1);
        when(promptTemplateMapper.selectById(101L)).thenReturn(targetVersion);
        when(promptTemplateMapper.selectVersionsForUpdate("task-breakdown.default"))
                .thenReturn(List.of(targetVersion));

        promptTemplateService.activate(101L);

        verify(promptTemplateMapper, never()).update(Mockito.isNull(), any());
    }

    @Test
    void deleteDisabledVersion_shouldRejectEnabledVersion() {
        PromptTemplate enabledVersion = buildTemplate(101L, 1, 1);
        when(promptTemplateMapper.selectById(101L)).thenReturn(enabledVersion);
        when(promptTemplateMapper.selectVersionsForUpdate("task-breakdown.default"))
                .thenReturn(List.of(enabledVersion));

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> promptTemplateService.deleteDisabledVersion(101L)
        );

        Assertions.assertEquals(ErrorCode.OPERATION_ERROR, exception.getErrorCode());
        verify(promptTemplateMapper, never()).deleteById(101L);
    }

    @Test
    void deleteDisabledVersion_shouldLogicallyDeleteDisabledVersion() {
        PromptTemplate disabledVersion = buildTemplate(102L, 2, 0);
        when(promptTemplateMapper.selectById(102L)).thenReturn(disabledVersion);
        when(promptTemplateMapper.selectVersionsForUpdate("task-breakdown.default"))
                .thenReturn(List.of(disabledVersion));
        when(promptTemplateMapper.deleteById(102L)).thenReturn(1);

        promptTemplateService.deleteDisabledVersion(102L);

        verify(promptTemplateMapper).deleteById(102L);
    }

    private PromptTemplateCreateVersionRequest buildCreateRequest() {
        PromptTemplateCreateVersionRequest request = new PromptTemplateCreateVersionRequest();
        request.setTemplateCode(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT.getCode());
        request.setTemplateName("任务拆解普通模式 V5");
        request.setTemplateContent("新的系统 Prompt 内容");
        request.setRemark("调整任务拆解约束");
        return request;
    }

    private PromptTemplate buildTemplate(Long id, Integer version, Integer enabled) {
        PromptTemplate template = new PromptTemplate();
        template.setId(id);
        template.setTemplateCode(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT.getCode());
        template.setScene(AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT.getScene().getCode());
        template.setTemplateName("任务拆解普通模式");
        template.setTemplateContent("模板内容");
        template.setVersion(version);
        template.setEnabled(enabled);
        template.setIsDelete(0);
        return template;
    }
}
