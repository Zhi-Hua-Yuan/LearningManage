package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.mapper.AiCallLogMapper;
import com.spt.learningmanage.model.entity.AiCallLog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

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
}
