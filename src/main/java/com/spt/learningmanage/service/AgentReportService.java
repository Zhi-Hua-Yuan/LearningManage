package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.agent.AgentReportConfirmRequest;
import com.spt.learningmanage.model.dto.agent.AgentReportQueryRequest;
import com.spt.learningmanage.model.vo.agent.AnalysisReportVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;

public interface AgentReportService {
    AiDraftConfirmVO confirm(AgentReportConfirmRequest request);

    Page<AnalysisReportVO> list(AgentReportQueryRequest request);

    AnalysisReportVO get(String reportId);

    boolean delete(String reportId);
}
