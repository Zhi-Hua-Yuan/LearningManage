package com.spt.learningmanage.model.vo.ai;

import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiBreakdownPreviewVO {
    private String draftId;
    private LocalDateTime expireAt;
    private List<MilestoneDraftVO> milestones;
}
