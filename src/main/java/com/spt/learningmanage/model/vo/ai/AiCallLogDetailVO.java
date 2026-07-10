package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiCallLogDetailVO {

    private Long id;

    private Long userId;

    private String scene;

    private String modelName;

    private String promptType;

    private Long promptTemplateId;

    private Integer promptVersion;

    private String promptSource;

    private String requestText;

    private Boolean requestTextTruncated;

    private String responseText;

    private Boolean responseTextTruncated;

    private Integer status;

    private String statusText;

    private String errorMessage;

    private Long costTimeMs;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
