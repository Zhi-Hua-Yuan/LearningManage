package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiDraftDetailVO {
    private String draftId;
    private String scene;
    private Integer status;
    private String statusText;
    private String payloadJson;
    private LocalDateTime expireAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
}
