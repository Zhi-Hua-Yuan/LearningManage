package com.spt.learningmanage.job;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.mapper.AiRagResultMapper;
import com.spt.learningmanage.model.entity.AiRagResult;
import com.spt.learningmanage.service.rag.RagResultViewService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagResultStatusRefreshJobTest {
    @Test
    void expiryPurgesAnswerBodyWhileKeepingMetadataRow() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        AiRagResultMapper mapper = mock(AiRagResultMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        RagResultStatusRefreshJob job = new RagResultStatusRefreshJob(
                properties, mapper, mock(RagResultViewService.class));

        job.refresh();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<AiRagResult>> wrapper = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), wrapper.capture());
        String sqlSet = wrapper.getValue().getSqlSet();
        assertTrue(sqlSet.contains("status"));
        assertTrue(sqlSet.contains("answer_text"));
        assertTrue(sqlSet.contains("answer_hash"));
    }
}
