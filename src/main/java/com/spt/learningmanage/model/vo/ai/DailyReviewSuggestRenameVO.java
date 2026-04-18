package com.spt.learningmanage.model.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "日报回顾改名建议结果")
public class DailyReviewSuggestRenameVO {

    @Schema(description = "建议批次ID", example = "20260418_rename_9ab27d5f")
    private String operationId;

    @Schema(description = "建议生成时间", example = "2026-04-18T21:10:12")
    private String generatedAt;

    @Schema(description = "回顾日期", example = "2026-04-18")
    private String reviewDate;

    @Schema(description = "改名建议列表")
    private List<TitleRenameSuggestionItemVO> items;
}

