package com.spt.learningmanage.model.vo.ops;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AiOpsOverviewVO {
    private LocalDateTime from;
    private LocalDateTime to;
    private AiOpsSummaryVO ai;
    private AiOpsSummaryVO rag;
    private AiOpsSummaryVO agent;
    private Map<String, Long> knowledgeQueue;
    private Map<String, DependencyStatusVO> dependencies;
}
