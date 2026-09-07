package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDataCleanupItemMapper;
import com.spt.learningmanage.mapper.AiDataCleanupRunMapper;
import com.spt.learningmanage.model.dto.ops.CleanupRunCreateRequest;
import com.spt.learningmanage.model.entity.AiDataCleanupItem;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.model.vo.ops.CleanupRunVO;
import com.spt.learningmanage.service.AdminOperationAuditService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleanupRunServiceImplTest {
    @Mock AiDataCleanupRunMapper runMapper;
    @Mock AiDataCleanupItemMapper itemMapper;
    @Mock PermissionService permissionService;
    @Mock AdminOperationAuditService audit;
    private CleanupRunServiceImpl service;

    @BeforeAll
    static void tableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), AiDataCleanupRun.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), AiDataCleanupItem.class);
    }

    @BeforeEach
    void setUp() {
        DataCleanupProperties properties = new DataCleanupProperties();
        properties.setEnabled(true);
        service = new CleanupRunServiceImpl(runMapper, itemMapper, properties,
                new DefaultDataRetentionPolicy(properties), permissionService, audit);
        UserHolder.set(7L);
    }

    @AfterEach
    void clear() {
        UserHolder.remove();
    }

    @Test
    void dryRunCreatesOneDurableItemPerResource() {
        when(runMapper.lockSubmission()).thenReturn(1);
        when(runMapper.selectByRequestForUpdate(anyLong(), anyString())).thenReturn(null);
        when(runMapper.selectActiveForUpdate()).thenReturn(null);
        when(runMapper.insert(any(AiDataCleanupRun.class))).thenReturn(1);
        when(itemMapper.insert(any(AiDataCleanupItem.class))).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        CleanupRunCreateRequest request = new CleanupRunCreateRequest();
        request.setDryRun(true);
        request.setClientRequestId("cleanup-request-1");

        CleanupRunVO result = service.submit(request);

        assertNotNull(result.getRunId());
        assertTrue(result.getDryRun());
        verify(permissionService).requireSystemAdmin(7L);
        verify(itemMapper, times(9)).insert(any(AiDataCleanupItem.class));
    }

    @Test
    void formalRunRequiresMatchingSuccessfulPreview() {
        when(runMapper.lockSubmission()).thenReturn(1);
        when(runMapper.selectByRequestForUpdate(anyLong(), anyString())).thenReturn(null);
        when(runMapper.selectActiveForUpdate()).thenReturn(null);
        when(runMapper.selectLatestDryRunForUpdate(anyString(), anyString(), any())).thenReturn(null);
        CleanupRunCreateRequest request = new CleanupRunCreateRequest();
        request.setDryRun(false);
        request.setClientRequestId("cleanup-request-2");

        BusinessException error = assertThrows(BusinessException.class, () -> service.submit(request));

        assertEquals(ErrorCode.CLEANUP_DRY_RUN_REQUIRED, error.getErrorCode());
        verify(runMapper, never()).insert(any(AiDataCleanupRun.class));
    }
}
