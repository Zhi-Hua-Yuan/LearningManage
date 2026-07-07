package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

@Data
public class AiCallLogStatusStatsVO {

    private Integer status;

    private String statusText;

    private Long count;
}
