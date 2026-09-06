package com.spt.learningmanage.model.vo.rag;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RagAnswerVO {
    private String requestId;
    private String status;
    private String answer;
    private Boolean insufficientEvidence;
    private Boolean degraded;
    private String degradationReason;
    private LocalDateTime knowledgeAsOf;
    private List<RagSourceVO> sources;
}
