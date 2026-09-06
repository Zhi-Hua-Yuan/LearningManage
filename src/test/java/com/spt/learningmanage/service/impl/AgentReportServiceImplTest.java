package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.agent.model.TeamMemberMetricSnapshot;
import com.spt.learningmanage.mapper.AiAnalysisReportMapper;
import com.spt.learningmanage.mapper.AiAnalysisReportSourceMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.model.entity.AiAnalysisReport;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.draft.AiDraftConfirmationService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentReportServiceImplTest {
    private final AiAnalysisReportMapper reportMapper = mock(AiAnalysisReportMapper.class);
    private final AiAnalysisReportSourceMapper sourceMapper = mock(AiAnalysisReportSourceMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final BusinessDataVersionService versionService = mock(BusinessDataVersionService.class);
    private final TeamMemberMapper memberMapper = mock(TeamMemberMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AgentReportServiceImpl service = new AgentReportServiceImpl(
            mock(AiDraftMapper.class), mock(AiDraftConfirmationService.class), reportMapper,
            sourceMapper, permissionService, versionService, memberMapper, objectMapper);

    @AfterEach
    void cleanup() { UserHolder.remove(); }

    @Test
    void memberReceivesOnlyPublicSummaryAndOwnMetric() throws Exception {
        UserHolder.set(7L);
        AiAnalysisReport report = teamReport();
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(sourceMapper.selectList(any())).thenReturn(List.of());
        when(versionService.teamVersion(2L)).thenReturn(3L);
        TeamMember member = new TeamMember();
        member.setRole("MEMBER");
        when(memberMapper.selectOne(any())).thenReturn(member);

        var result = service.get("report-1");

        assertEquals("public", result.summary());
        assertTrue(result.memberMetrics().containsKey("self"));
        assertFalse(result.memberMetrics().containsKey("members"));
    }

    @Test
    void adminReceivesManagerSummaryAndAllMetrics() throws Exception {
        UserHolder.set(7L);
        AiAnalysisReport report = teamReport();
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(sourceMapper.selectList(any())).thenReturn(List.of());
        when(versionService.teamVersion(2L)).thenReturn(3L);
        TeamMember member = new TeamMember();
        member.setRole("ADMIN");
        when(memberMapper.selectOne(any())).thenReturn(member);

        var result = service.get("report-1");

        assertEquals("manager", result.summary());
        assertTrue(result.memberMetrics().containsKey("members"));
    }

    private AiAnalysisReport teamReport() throws Exception {
        AiAnalysisReport report = new AiAnalysisReport();
        report.setReportId("report-1");
        report.setReportType("TEAM_WORKLOAD");
        report.setTeamId(2L);
        report.setSourceDataVersion(3L);
        report.setManagerSummary("manager");
        report.setPublicSummary("public");
        report.setGeneratedAt(LocalDateTime.now());
        report.setRecommendationsJson("[]");
        report.setMemberMetricsJson(objectMapper.writeValueAsString(List.of(
                new TeamMemberMetricSnapshot(7L, 2, 0, 1, 3, 3, 3,
                        BigDecimal.valueOf(100), "LOW", "team-workload-v1"),
                new TeamMemberMetricSnapshot(8L, 9, 4, 8, 0, 0, 0,
                        null, "HIGH", "team-workload-v1"))));
        return report;
    }
}
