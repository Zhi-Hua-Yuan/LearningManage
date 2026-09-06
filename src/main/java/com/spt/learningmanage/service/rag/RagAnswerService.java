package com.spt.learningmanage.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.ai.pipeline.AiContentLoggingPolicy;
import com.spt.learningmanage.ai.pipeline.AiExecutionCommand;
import com.spt.learningmanage.ai.pipeline.AiExecutionResult;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.model.rag.RagAnswerContent;
import com.spt.learningmanage.model.rag.RagContext;
import com.spt.learningmanage.model.rag.RagGeneratedAnswer;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RagAnswerService {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "answer", "insufficientEvidence", "citations");

    private final AiInvocationPipeline pipeline;
    private final AiProperties aiProperties;
    private final AiJsonResponseSanitizer responseSanitizer;
    private final RagCitationValidator citationValidator;
    private final ObjectMapper objectMapper;

    public RagAnswerService(AiInvocationPipeline pipeline,
                            AiProperties aiProperties,
                            AiJsonResponseSanitizer responseSanitizer,
                            RagCitationValidator citationValidator,
                            ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.aiProperties = aiProperties;
        this.responseSanitizer = responseSanitizer;
        this.citationValidator = citationValidator;
        this.objectMapper = objectMapper;
    }

    public RagGeneratedAnswer generate(Long actorUserId, RagContext context, String traceId) {
        try {
            return execute(actorUserId, context, traceId, false);
        } catch (AiResponseProcessingException firstFailure) {
            try {
                return execute(actorUserId, context, traceId, true);
            } catch (RuntimeException secondFailure) {
                secondFailure.addSuppressed(firstFailure);
                throw secondFailure;
            }
        }
    }

    private RagGeneratedAnswer execute(Long actorUserId,
                                        RagContext context,
                                        String traceId,
                                        boolean repair) {
        String prompt = repair
                ? context.userPrompt() + "\n\n上一次响应未通过 JSON 或引用校验。请重新生成，并严格遵守输出结构。"
                : context.userPrompt();
        String safeSummary = repair
                ? context.safeLogSummary() + "|repairAttempt=true"
                : context.safeLogSummary();
        AiExecutionResult<RagAnswerContent> result = pipeline.execute(new AiExecutionCommand(
                actorUserId,
                aiProperties.getModel(),
                AiPromptCodeEnum.RAG_PROJECT_ANSWER,
                prompt,
                "RAG 回答或引用格式异常",
                traceId,
                AiContentLoggingPolicy.METADATA_ONLY,
                safeSummary
        ), raw -> citationValidator.validate(parse(raw), context.evidence()));
        return new RagGeneratedAnswer(result.data(), result.callLogId(), result.actualModel(),
                result.promptCode(), result.promptVersion(), result.degraded(),
                result.degradationReason());
    }

    private RagAnswerContent parse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(responseSanitizer.sanitizeObject(raw));
            if (!root.isObject()) {
                throw new IllegalArgumentException("RAG response must be an object");
            }
            Set<String> fields = new HashSet<>();
            root.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(ALLOWED_FIELDS)
                    || !root.path("answer").isTextual()
                    || !root.path("insufficientEvidence").isBoolean()
                    || !root.path("citations").isArray()) {
                throw new IllegalArgumentException("RAG response schema mismatch");
            }
            List<String> citations = new ArrayList<>();
            root.path("citations").forEach(value -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("RAG citation must be a string");
                }
                citations.add(value.asText());
            });
            return new RagAnswerContent(root.path("answer").asText(),
                    root.path("insufficientEvidence").asBoolean(), citations);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to parse RAG response", exception);
        }
    }
}
