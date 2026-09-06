package com.spt.learningmanage.model.rag;

public record RagGeneratedAnswer(
        RagAnswerContent content,
        Long aiCallLogId,
        String actualModel,
        String promptCode,
        Integer promptVersion,
        boolean degraded,
        String degradationReason
) {
}
