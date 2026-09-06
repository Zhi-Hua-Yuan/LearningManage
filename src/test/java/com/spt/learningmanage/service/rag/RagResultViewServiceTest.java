package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.constant.RagSourceValidationStatus;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiRagResultMapper;
import com.spt.learningmanage.mapper.AiRagResultSourceMapper;
import com.spt.learningmanage.model.entity.AiRagResult;
import com.spt.learningmanage.model.entity.AiRagResultSource;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagResultViewServiceTest {
    private final AiRagResultMapper resultMapper = mock(AiRagResultMapper.class);
    private final AiRagResultSourceMapper sourceMapper = mock(AiRagResultSourceMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final RagSourceVerifier verifier = mock(RagSourceVerifier.class);
    private final RagResultViewService service = new RagResultViewService(
            resultMapper, sourceMapper, permissionService, verifier);

    @Test
    void staleSourceReturnsStatusWithoutOldAnswerBody() {
        AiRagResult result = activeResult();
        AiRagResultSource source = source();
        ProjectAccessScope scope = new ProjectAccessScope(7L, 10L, 7L, null, null);
        when(resultMapper.selectOne(any())).thenReturn(result);
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));
        when(permissionService.requireProjectView(7L, 10L)).thenReturn(scope);
        when(verifier.verifyStored(7L, scope, List.of(source)))
                .thenReturn(RagSourceValidationStatus.STALE);

        var response = service.get(7L, "request-1");

        assertEquals("STALE", response.getStatus());
        assertNull(response.getAnswer());
        verify(resultMapper).update(any(), any());
    }

    @Test
    void expiredResultNeverReturnsAnswerBody() {
        AiRagResult result = activeResult();
        result.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(resultMapper.selectOne(any())).thenReturn(result);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.get(7L, "request-1"));

        assertEquals(ErrorCode.RAG_RESULT_EXPIRED, exception.getErrorCode());
        verify(resultMapper).update(any(), any());
    }

    @Test
    void permissionLossInvalidatesUserOwnedResult() {
        AiRagResult result = activeResult();
        when(resultMapper.selectOne(any())).thenReturn(result);
        when(permissionService.requireProjectView(7L, 10L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN_ERROR));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.get(7L, "request-1"));

        assertEquals(ErrorCode.FORBIDDEN_ERROR, exception.getErrorCode());
        verify(resultMapper).update(any(), any());
    }

    private AiRagResult activeResult() {
        AiRagResult result = new AiRagResult();
        result.setId(1L);
        result.setRequestId("request-1");
        result.setUserId(7L);
        result.setProjectId(10L);
        result.setStatus("ACTIVE");
        result.setAnswerText("旧回答 [S1]");
        result.setInsufficientEvidence(0);
        result.setDegraded(0);
        result.setExpiresAt(LocalDateTime.now().plusDays(1));
        return result;
    }

    private AiRagResultSource source() {
        AiRagResultSource source = new AiRagResultSource();
        source.setCitationId("S1");
        source.setSourceType("TASK");
        source.setSourceId(20L);
        source.setVectorScore(0.8);
        source.setTitleSnapshot("任务");
        return source;
    }
}
