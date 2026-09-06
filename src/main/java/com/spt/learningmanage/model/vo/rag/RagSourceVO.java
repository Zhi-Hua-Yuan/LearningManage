package com.spt.learningmanage.model.vo.rag;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagSourceVO {
    private String citationId;
    private String sourceType;
    private Long sourceId;
    private String title;
    private Double score;
    private Double vectorScore;
    private Double rerankScore;
    private LocalDateTime updatedAt;
}
