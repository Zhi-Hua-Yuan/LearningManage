package com.spt.learningmanage.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class MilestoneDraftVO {
    private String name;
    private List<TaskDraftVO> tasks;
}

