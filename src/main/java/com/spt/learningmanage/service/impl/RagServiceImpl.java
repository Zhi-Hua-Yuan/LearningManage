package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.ai.governance.AiSanitizationStatus;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.RagSourceValidationStatus;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.entity.AiRagQueryLog;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.PersistedRagResult;
import com.spt.learningmanage.model.rag.RagAnswerContent;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.model.rag.RagContext;
import com.spt.learningmanage.model.rag.RagGeneratedAnswer;
import com.spt.learningmanage.model.rag.RagRetrievalOutcome;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.RagService;
import com.spt.learningmanage.service.rag.RagAnswerService;
import com.spt.learningmanage.service.rag.RagContextAssembler;
import com.spt.learningmanage.service.rag.RagQueryAuditService;
import com.spt.learningmanage.service.rag.RagQuestionHasher;
import com.spt.learningmanage.service.rag.RagReadinessService;
import com.spt.learningmanage.service.rag.RagResultPersistenceService;
import com.spt.learningmanage.service.rag.RagResultViewService;
import com.spt.learningmanage.service.rag.RagRetrievalService;
import com.spt.learningmanage.service.rag.RagSourceVerifier;
import com.spt.learningmanage.trace.TraceContext;
import com.spt.learningmanage.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RagServiceImpl implements RagService {
    private static final String INSUFFICIENT_ANSWER =
            "依据不足，当前项目中没有找到足够相关且可访问的记录。";

    private final RagProperties properties;
    private final RagReadinessService readinessService;
    private final PermissionService permissionService;
    private final AiContentSanitizer contentSanitizer;
    private final RagQuestionHasher questionHasher;
    private final RagQueryAuditService auditService;
    private final RagRetrievalService retrievalService;
    private final RagContextAssembler contextAssembler;
    private final RagAnswerService answerService;
    private final RagSourceVerifier sourceVerifier;
    private final RagResultPersistenceService persistenceService;
    private final RagResultViewService viewService;

    public RagServiceImpl(RagProperties properties,
                          RagReadinessService readinessService,
                          PermissionService permissionService,
                          AiContentSanitizer contentSanitizer,
                          RagQuestionHasher questionHasher,
                          RagQueryAuditService auditService,
                          RagRetrievalService retrievalService,
                          RagContextAssembler contextAssembler,
                          RagAnswerService answerService,
                          RagSourceVerifier sourceVerifier,
                          RagResultPersistenceService persistenceService,
                          RagResultViewService viewService) {
        this.properties = properties;
        this.readinessService = readinessService;
        this.permissionService = permissionService;
        this.contentSanitizer = contentSanitizer;
        this.questionHasher = questionHasher;
        this.auditService = auditService;
        this.retrievalService = retrievalService;
        this.contextAssembler = contextAssembler;
        this.answerService = answerService;
        this.sourceVerifier = sourceVerifier;
        this.persistenceService = persistenceService;
        this.viewService = viewService;
    }

    @Override
    public RagAnswerVO ask(RagAskRequest request) {
        Long actorUserId = currentUserId();
        validateRequest(request);
        readinessService.requireReady();
        ProjectAccessScope initialScope = permissionService.requireProjectView(
                actorUserId, request.getProjectId());
        String question = sanitizeQuestion(request.getQuestion());
        String requestId = UUID.randomUUID().toString();
        String traceId = TraceContext.currentOrCreate();
        long startedAt = System.currentTimeMillis();
        AiRagQueryLog queryLog = auditService.start(requestId, actorUserId,
                request.getProjectId(), questionHasher.hmac(question), traceId);
        try {
            ProjectAccessScope scope = initialScope;
            for (int attempt = 0; attempt < 2; attempt++) {
                RagRetrievalOutcome retrieval = retrievalService.retrieve(
                        actorUserId, scope, question, traceId);
                RagContext context = contextAssembler.assemble(question, retrieval.candidates());
                RagGeneratedAnswer generated = retrieval.candidates().isEmpty()
                        ? insufficientAnswer()
                        : answerService.generate(actorUserId, context, traceId);
                List<RagCandidate> cited = generated.content().citations().stream()
                        .map(context.evidence()::get)
                        .toList();

                scope = permissionService.requireProjectView(actorUserId, request.getProjectId());
                RagSourceValidationStatus validation = sourceVerifier.verifyCandidates(
                        actorUserId, scope, cited);
                if (validation == RagSourceValidationStatus.VALID) {
                    PersistedRagResult persisted = persistenceService.save(
                            requestId, actorUserId, request.getProjectId(), traceId,
                            queryLog, retrieval, context, generated, elapsed(startedAt));
                    return viewService.toVO(persisted);
                }
                if (attempt == 1) {
                    throw new BusinessException(ErrorCode.RAG_SOURCE_CHANGED);
                }
            }
            throw new BusinessException(ErrorCode.RAG_SOURCE_CHANGED);
        } catch (RuntimeException exception) {
            try {
                auditService.fail(queryLog.getId(), failureType(exception), elapsed(startedAt));
            } catch (RuntimeException auditFailure) {
                exception.addSuppressed(auditFailure);
            }
            throw exception;
        }
    }

    @Override
    public RagAnswerVO getResult(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "requestId 不合法");
        }
        return viewService.get(currentUserId(), requestId.trim());
    }

    private RagGeneratedAnswer insufficientAnswer() {
        return new RagGeneratedAnswer(
                new RagAnswerContent(INSUFFICIENT_ANSWER, true, List.of()),
                null, null, AiPromptCodeEnum.RAG_PROJECT_ANSWER.getCode(), 1,
                false, null);
    }

    private String sanitizeQuestion(String question) {
        var sanitized = contentSanitizer.sanitizeForProvider(question.trim());
        if (sanitized.status() == AiSanitizationStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.AI_CONTENT_BLOCKED);
        }
        if (sanitized.value() == null || sanitized.value().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题脱敏后为空");
        }
        return sanitized.value();
    }

    private void validateRequest(RagAskRequest request) {
        if (request == null || request.getProjectId() == null || request.getProjectId() <= 0
                || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (request.getQuestion().length() > properties.getMaxQuestionChars()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题长度超出限制");
        }
    }

    private Long currentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }

    private long elapsed(long startedAt) {
        return Math.max(System.currentTimeMillis() - startedAt, 0L);
    }

    private String failureType(RuntimeException exception) {
        if (exception instanceof BusinessException business) {
            return business.getErrorCode().name();
        }
        return exception.getClass().getSimpleName();
    }
}
