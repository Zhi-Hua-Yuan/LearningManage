package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiCallLogVO {

    private Long id;

    private String scene;

    private String modelName;

    private String promptType;

    private String requestPreview;

    private String responsePreview;

    private Integer status;

    private String statusText;

    private String errorMessage;

    private Long costTimeMs;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
