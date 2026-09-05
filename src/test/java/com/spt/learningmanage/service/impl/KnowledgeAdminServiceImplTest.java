package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.config.QdrantProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.mapper.AiKnowledgeDocumentMapper;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.dto.knowledge.KnowledgeBackfillCreateRequest;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.model.entity.AiKnowledgeDocument;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeAdminServiceImplTest {

    @BeforeAll
    static void initTables() {
        init(AiKnowledgeIndexEvent.class);
        init(AiKnowledgeDocument.class);
        init(AiKnowledgeBackfillRun.class);
    }

    @AfterEach
    void clearActor() {
        UserHolder.remove();
    }

    @Test
    void createsIdempotentPendingBackfillForSystemAdmin() {
        Fixture fixture = new Fixture(true);
        UserHolder.set(9L);
        when(fixture.backfillMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(fixture.backfillMapper.insert(any(AiKnowledgeBackfillRun.class))).thenAnswer(invocation -> {
            AiKnowledgeBackfillRun run = invocation.getArgument(0);
            run.setId(7L);
            return 1;
        });
        KnowledgeBackfillCreateRequest request = new KnowledgeBackfillCreateRequest();
        request.setRunKey("stage4-initial-v1");
        request.setRunType("INITIAL");
        request.setSourceScope("ALL");
        request.setBatchSize(500);

        var result = fixture.service().createBackfill(request);

        assertEquals(7L, result.getRunId());
        assertEquals("PENDING", result.getStatus());
        assertFalse(result.isIdempotentReplay());
        verify(fixture.permissionService).requireSystemAdmin(9L);
    }

    @Test
    void disabledWorkerRejectsBackfillButStatusRemainsReadable() {
        Fixture fixture = new Fixture(false);
        UserHolder.set(9L);
        KnowledgeBackfillCreateRequest request = new KnowledgeBackfillCreateRequest();
        request.setRunKey("stage4-initial-v1");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().createBackfill(request));
        assertEquals(ErrorCode.KNOWLEDGE_INDEX_DISABLED, exception.getErrorCode());
        fixture.service().status();
        verify(fixture.permissionService, org.mockito.Mockito.atLeastOnce()).requireSystemAdmin(9L);
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), type);
        }
    }

    private static final class Fixture {
        private final AiKnowledgeIndexEventMapper eventMapper = mock(AiKnowledgeIndexEventMapper.class);
        private final AiKnowledgeDocumentMapper documentMapper = mock(AiKnowledgeDocumentMapper.class);
        private final AiKnowledgeBackfillRunMapper backfillMapper = mock(AiKnowledgeBackfillRunMapper.class);
        private final PermissionService permissionService = mock(PermissionService.class);
        private final KnowledgeIndexProperties indexProperties = new KnowledgeIndexProperties();

        private Fixture(boolean enabled) {
            indexProperties.setWorkerEnabled(enabled);
        }

        private KnowledgeAdminServiceImpl service() {
            return new KnowledgeAdminServiceImpl(eventMapper, documentMapper, backfillMapper,
                    permissionService, indexProperties, new EmbeddingProperties(), new QdrantProperties());
        }
    }
}
