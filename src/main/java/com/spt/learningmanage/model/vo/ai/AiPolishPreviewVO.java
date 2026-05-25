package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiPolishPreviewVO {
    private String draftId;
    private LocalDateTime expireAt;
    private String review;
}
