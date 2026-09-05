package com.spt.learningmanage.service.knowledge;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeEventQueueServiceTest {

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(AiKnowledgeIndexEvent.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""),
                    AiKnowledgeIndexEvent.class);
        }
    }

    @Test
    void claimAssignsFreshFencingTokenAndLease() {
        AiKnowledgeIndexEventMapper mapper = mock(AiKnowledgeIndexEventMapper.class);
        AiKnowledgeIndexEvent event = event();
        when(mapper.selectReadyForUpdate(any(), anyInt())).thenReturn(List.of(event));
        when(mapper.updateById(event)).thenReturn(1);
        KnowledgeEventQueueService service = new KnowledgeEventQueueService(mapper,
                new KnowledgeIndexProperties());

        List<AiKnowledgeIndexEvent> claimed = service.claimReady("worker-1", 20);

        assertEquals("PROCESSING", claimed.get(0).getStatus());
        assertEquals("worker-1", claimed.get(0).getClaimedBy());
        assertNotNull(claimed.get(0).getClaimToken());
        assertNotNull(claimed.get(0).getLeaseUntil());
    }

    @Test
    void nonRetryableFailureTransitionsDirectlyToDead() {
        AiKnowledgeIndexEventMapper mapper = mock(AiKnowledgeIndexEventMapper.class);
        when(mapper.update(any(), any(Wrapper.class))).thenReturn(1);
        KnowledgeEventQueueService service = new KnowledgeEventQueueService(mapper,
                new KnowledgeIndexProperties());

        assertTrue(service.markFailure(event(), KnowledgeFailureTypeEnum.DIMENSION_MISMATCH,
                false, "wrong\nvector dimension"));

        verify(mapper).update(any(), any(Wrapper.class));
    }

    private AiKnowledgeIndexEvent event() {
        AiKnowledgeIndexEvent event = new AiKnowledgeIndexEvent();
        event.setId(1L);
        event.setSourceType("TASK");
        event.setSourceId(2L);
        event.setStatus("PENDING");
        event.setAttemptCount(0);
        event.setClaimToken("token");
        return event;
    }
}
