package com.spt.learningmanage.model.vo.milestone;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI 生成的里程碑草稿")
public class MilestoneDraftVO {

    @Schema(description = "里程碑名称", example = "第一阶段：词汇与听力基础")
    private String name;

    @Schema(description = "该里程碑下的任务草稿列表")
    private List<TaskDraftVO> tasks;
}
