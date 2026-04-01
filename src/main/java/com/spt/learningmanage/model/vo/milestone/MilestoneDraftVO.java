package com.spt.learningmanage.model.vo.milestone;

import lombok.Data;

import java.util.List;

@Data
public class MilestoneDraftVO {
    private String name;
    private List<TaskDraftVO> tasks;
}

