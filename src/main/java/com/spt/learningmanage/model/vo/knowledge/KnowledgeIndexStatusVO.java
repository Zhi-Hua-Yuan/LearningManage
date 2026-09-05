package com.spt.learningmanage.model.vo.knowledge;

import lombok.Data;

import java.util.Map;

@Data
public class KnowledgeIndexStatusVO {
    private boolean workerEnabled;
    private String embeddingModel;
    private Integer embeddingDimension;
    private String collection;
    private String alias;
    private Map<String, Long> eventCounts;
    private Map<String, Long> documentCounts;
    private Map<String, Long> backfillCounts;
}
