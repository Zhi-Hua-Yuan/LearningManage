package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.vo.ops.AiOpsOverviewVO;
import com.spt.learningmanage.model.vo.ops.AiOpsSummaryVO;
import com.spt.learningmanage.model.vo.ops.DependencyStatusVO;
import com.spt.learningmanage.model.vo.ops.OpsFailureVO;

import java.time.LocalDateTime;
import java.util.Map;

public interface AiOpsQueryService {
    AiOpsOverviewVO overview(LocalDateTime from, LocalDateTime to);
    AiOpsSummaryVO rag(LocalDateTime from, LocalDateTime to);
    AiOpsSummaryVO agent(LocalDateTime from, LocalDateTime to);
    Page<OpsFailureVO> failures(LocalDateTime from, LocalDateTime to, long current, long size);
    Map<String, DependencyStatusVO> dependencies();
}
