package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.mapper.AiCallLogMapper;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.entity.AiCallLog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiCallLogServiceImplTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @InjectMocks
    private AiCallLogServiceImpl aiCallLogService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiCallLog.class
        );
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void updateExecutionMetadata_shouldUpdateActualModelAndRetryCount() {
        aiCallLogService.updateExecutionMetadata(100L, "fallback-model", 1);

        verify(aiCallLogMapper).update(isNull(), any());
    }

    @Test
    void markTimeout_shouldPersistTimeoutStatus() {
        aiCallLogService.markTimeout(100L, "AI 服务响应超时", 60000L);

        verify(aiCallLogMapper).update(isNull(), any());
    }

    @Test
    void complete_shouldUseRunningStatusAsCasCondition() {
        when(aiCallLogMapper.update(isNull(), any())).thenReturn(1, 0);
        AiCallLogCompletionCommand command = new AiCallLogCompletionCommand(
                100L, AiCallLogStatusEnum.SUCCESS, "response", null, 10L,
                "requested", "actual", 0, "stop", null, "provider-1",
                false, null, "trace-1", null, false, null
        );

        assertTrue(aiCallLogService.complete(command));
        assertFalse(aiCallLogService.complete(command));

        ArgumentCaptor<Wrapper<AiCallLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(aiCallLogMapper, org.mockito.Mockito.times(2)).update(isNull(), captor.capture());
        Wrapper<AiCallLog> wrapper = captor.getAllValues().get(0);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("id"));
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> updateWrapper =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper;
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(100L));
        assertTrue(updateWrapper.getParamNameValuePairs()
                .containsValue(AiCallLogStatusEnum.RUNNING.getValue()));
    }
}
