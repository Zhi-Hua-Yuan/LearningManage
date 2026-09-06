package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.ai.governance.AiSanitizationStatus;
import com.spt.learningmanage.ai.governance.AiSanitizedContent;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.RagSourceValidationStatus;
import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.entity.AiRagQueryLog;
import com.spt.learningmanage.model.entity.AiRagResult;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.PersistedRagResult;
import com.spt.learningmanage.model.rag.RagAnswerContent;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.model.rag.RagContext;
import com.spt.learningmanage.model.rag.RagGeneratedAnswer;
import com.spt.learningmanage.model.rag.RagRetrievalOutcome;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.rag.RagAnswerService;
import com.spt.learningmanage.service.rag.RagContextAssembler;
import com.spt.learningmanage.service.rag.RagQueryAuditService;
import com.spt.learningmanage.service.rag.RagQuestionHasher;
import com.spt.learningmanage.service.rag.RagReadinessService;
import com.spt.learningmanage.service.rag.RagResultPersistenceService;
import com.spt.learningmanage.service.rag.RagResultViewService;
import com.spt.learningmanage.service.rag.RagRetrievalService;
import com.spt.learningmanage.service.rag.RagSourceVerifier;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {
    @Mock RagReadinessService readiness;
    @Mock PermissionService permissionService;
    @Mock AiContentSanitizer sanitizer;
    @Mock RagQuestionHasher hasher;
    @Mock RagQueryAuditService audit;
    @Mock RagRetrievalService retrieval;
    @Mock RagContextAssembler assembler;
    @Mock RagAnswerService answerService;
    @Mock RagSourceVerifier verifier;
    @Mock RagResultPersistenceService persistence;
    @Mock RagResultViewService views;

    private RagServiceImpl service;
    private ProjectAccessScope scope;
    private AiRagQueryLog queryLog;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        service = new RagServiceImpl(properties, readiness, permissionService, sanitizer,
                hasher, audit, retrieval, assembler, answerService, verifier, persistence, views);
        scope = new ProjectAccessScope(7L, 10L, 7L, null, null);
        queryLog = new AiRagQueryLog();
        queryLog.setId(99L);
        when(permissionService.requireProjectView(7L, 10L)).thenReturn(scope);
        when(sanitizer.sanitizeForProvider("为什么延期"))
                .thenReturn(new AiSanitizedContent("为什么延期", AiSanitizationStatus.CLEAN,
                        false, "q"));
        when(hasher.hmac("为什么延期")).thenReturn("a".repeat(64));
        when(audit.start(anyString(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(queryLog);
        UserHolder.set(7L);
    }

    @AfterEach
    void clear() {
        UserHolder.remove();
    }

    @Test
    void noCandidateReturnsDeterministicInsufficientAnswerWithoutChatCall() {
        RagRetrievalOutcome outcome = new RagRetrievalOutcome(
                List.of(), 0, 0, false, null, "text-embedding-v4", null);
        RagContext context = new RagContext("prompt", "{\"evidenceCount\":0}", Map.of());
        when(retrieval.retrieve(anyLong(), any(), anyString(), anyString())).thenReturn(outcome);
        when(assembler.assemble("为什么延期", List.of())).thenReturn(context);
        when(verifier.verifyCandidates(7L, scope, List.of()))
                .thenReturn(RagSourceValidationStatus.VALID);
        PersistedRagResult persisted = persisted();
        when(persistence.save(anyString(), anyLong(), anyLong(), anyString(), any(),
                any(), any(), any(), anyLong())).thenReturn(persisted);
        when(views.toVO(persisted)).thenReturn(new RagAnswerVO());

        service.ask(request());

        verify(answerService, never()).generate(anyLong(), any(), anyString());
        verify(persistence).save(anyString(), anyLong(), anyLong(), anyString(), any(),
                any(), any(), any(), anyLong());
    }

    @Test
    void sourceChangeForcesOneCompleteRetrievalAndGenerationRetry() {
        RagCandidate candidate = candidate();
        RagRetrievalOutcome outcome = new RagRetrievalOutcome(
                List.of(candidate), 1, 1, false, null, "text-embedding-v4", "qwen3-rerank");
        RagContext context = new RagContext("prompt", "{\"evidenceCount\":1}",
                Map.of("S1", candidate));
        RagGeneratedAnswer generated = new RagGeneratedAnswer(
                new RagAnswerContent("延期原因 [S1]", false, List.of("S1")),
                1L, "qwen-plus", "rag-project-answer", 1, false, null);
        when(retrieval.retrieve(anyLong(), any(), anyString(), anyString())).thenReturn(outcome);
        when(assembler.assemble("为什么延期", List.of(candidate))).thenReturn(context);
        when(answerService.generate(anyLong(), any(), anyString())).thenReturn(generated);
        when(verifier.verifyCandidates(7L, scope, List.of(candidate)))
                .thenReturn(RagSourceValidationStatus.STALE, RagSourceValidationStatus.VALID);
        PersistedRagResult persisted = persisted();
        when(persistence.save(anyString(), anyLong(), anyLong(), anyString(), any(),
                any(), any(), any(), anyLong())).thenReturn(persisted);
        when(views.toVO(persisted)).thenReturn(new RagAnswerVO());

        service.ask(request());

        verify(retrieval, times(2)).retrieve(anyLong(), any(), anyString(), anyString());
        verify(answerService, times(2)).generate(anyLong(), any(), anyString());
        verify(persistence, times(1)).save(anyString(), anyLong(), anyLong(), anyString(),
                any(), any(), any(), any(), anyLong());
    }

    private RagAskRequest request() {
        RagAskRequest request = new RagAskRequest();
        request.setProjectId(10L);
        request.setQuestion("为什么延期");
        return request;
    }

    private RagCandidate candidate() {
        return new RagCandidate("p1", "p1", "TASK:1:PRIVATE:10",
                KnowledgeSourceTypeEnum.TASK, 1L, 0, "任务", "正文",
                "a".repeat(64), "b".repeat(64), 0.8, 0.9, null);
    }

    private PersistedRagResult persisted() {
        AiRagResult result = new AiRagResult();
        result.setId(1L);
        return new PersistedRagResult(result, List.of());
    }
}
