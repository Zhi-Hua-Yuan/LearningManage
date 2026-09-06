package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.agent.model.TeamMemberMetricSnapshot;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.constant.AnalysisReportTypeEnum;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiAnalysisReportMapper;
import com.spt.learningmanage.mapper.AiAnalysisReportSourceMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.model.dto.agent.AgentReportConfirmRequest;
import com.spt.learningmanage.model.dto.agent.AgentReportQueryRequest;
import com.spt.learningmanage.model.dto.ai.draft.AgentReportConfirmationContext;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationCommand;
import com.spt.learningmanage.model.entity.AiAnalysisReport;
import com.spt.learningmanage.model.entity.AiAnalysisReportSource;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.vo.agent.AnalysisReportVO;
import com.spt.learningmanage.model.vo.agent.ReportSourceVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.service.AgentReportService;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.draft.AiDraftConfirmationService;
import com.spt.learningmanage.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentReportServiceImpl implements AgentReportService {
    private final AiDraftMapper draftMapper;
    private final AiDraftConfirmationService confirmationService;
    private final AiAnalysisReportMapper reportMapper;
    private final AiAnalysisReportSourceMapper sourceMapper;
    private final PermissionService permissionService;
    private final BusinessDataVersionService versionService;
    private final TeamMemberMapper teamMemberMapper;
    private final ObjectMapper objectMapper;

    public AgentReportServiceImpl(AiDraftMapper draftMapper,
                                  AiDraftConfirmationService confirmationService,
                                  AiAnalysisReportMapper reportMapper,
                                  AiAnalysisReportSourceMapper sourceMapper,
                                  PermissionService permissionService,
                                  BusinessDataVersionService versionService,
                                  TeamMemberMapper teamMemberMapper,
                                  ObjectMapper objectMapper) {
        this.draftMapper = draftMapper;
        this.confirmationService = confirmationService;
        this.reportMapper = reportMapper;
        this.sourceMapper = sourceMapper;
        this.permissionService = permissionService;
        this.versionService = versionService;
        this.teamMemberMapper = teamMemberMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiDraftConfirmVO confirm(AgentReportConfirmRequest request) {
        Long actor = currentUserId();
        AiDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<AiDraft>()
                .eq(AiDraft::getDraftId, request.getDraftId().trim())
                .eq(AiDraft::getUserId, actor).last("limit 1"));
        if (draft == null || !(AgentSceneEnum.PROJECT_RISK.getDraftScene().equals(draft.getScene())
                || AgentSceneEnum.TEAM_WORKLOAD.getDraftScene().equals(draft.getScene()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Agent 报告草稿不存在");
        }
        return confirmationService.confirm(new AiDraftConfirmationCommand(
                actor, draft.getDraftId(), request.getOperationId().trim(), draft.getScene(),
                new AgentReportConfirmationContext()));
    }

    @Override
    public Page<AnalysisReportVO> list(AgentReportQueryRequest request) {
        AgentReportQueryRequest query = request == null ? new AgentReportQueryRequest() : request;
        String type = normalizeType(query.getReportType());
        long offset = (query.getCurrent() - 1) * query.getPageSize();
        Long actor = currentUserId();
        List<AiAnalysisReport> records = reportMapper.selectAccessiblePage(actor, type,
                query.getProjectId(), query.getTeamId(), offset, query.getPageSize());
        long total = reportMapper.countAccessible(actor, type, query.getProjectId(), query.getTeamId());
        Page<AnalysisReportVO> page = new Page<>(query.getCurrent(), query.getPageSize(), total);
        page.setRecords(records.stream().map(value -> toVO(actor, value)).toList());
        return page;
    }

    @Override
    public AnalysisReportVO get(String reportId) {
        return toVO(currentUserId(), requireReport(reportId));
    }

    @Override
    public boolean delete(String reportId) {
        Long actor = currentUserId();
        AiAnalysisReport report = requireReport(reportId);
        authorizeDelete(actor, report);
        return reportMapper.update(null, new LambdaUpdateWrapper<AiAnalysisReport>()
                .eq(AiAnalysisReport::getId, report.getId())
                .eq(AiAnalysisReport::getIsDelete, 0)
                .set(AiAnalysisReport::getIsDelete, 1)
                .set(AiAnalysisReport::getDeletedAt, LocalDateTime.now())) == 1;
    }

    private AnalysisReportVO toVO(Long actor, AiAnalysisReport report) {
        boolean manager = authorizeView(actor, report);
        boolean stale = currentVersion(report) != report.getSourceDataVersion();
        List<TeamMemberMetricSnapshot> metrics = readMetrics(report.getMemberMetricsJson());
        Map<String, Object> visibleMetrics = new LinkedHashMap<>();
        if (AnalysisReportTypeEnum.TEAM_WORKLOAD.name().equals(report.getReportType())) {
            if (manager) {
                visibleMetrics.put("members", metrics);
            } else {
                metrics.stream().filter(value -> actor.equals(value.userId())).findFirst()
                        .ifPresent(value -> visibleMetrics.put("self", value));
            }
        }
        String summary = AnalysisReportTypeEnum.TEAM_WORKLOAD.name().equals(report.getReportType()) && !manager
                ? report.getPublicSummary() : report.getManagerSummary();
        return new AnalysisReportVO(report.getReportId(), report.getReportType(), report.getProjectId(),
                report.getTeamId(), stale ? "STALE" : "ACTIVE", summary, visibleMetrics,
                readRecommendations(report.getRecommendationsJson()), visibleSources(actor, report),
                report.getGeneratedAt());
    }

    private boolean authorizeView(Long actor, AiAnalysisReport report) {
        if (AnalysisReportTypeEnum.PROJECT_RISK.name().equals(report.getReportType())) {
            return permissionService.requireProjectView(actor, report.getProjectId()).canManage();
        }
        permissionService.requireTeamView(actor, report.getTeamId());
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, report.getTeamId()).eq(TeamMember::getUserId, actor)
                .eq(TeamMember::getIsDelete, 0).last("limit 1"));
        TeamRoleEnum role = member == null ? null : TeamRoleEnum.fromValue(member.getRole());
        return role == TeamRoleEnum.OWNER || role == TeamRoleEnum.ADMIN;
    }

    private void authorizeDelete(Long actor, AiAnalysisReport report) {
        if (actor.equals(report.getCreatorUserId())) return;
        if (AnalysisReportTypeEnum.PROJECT_RISK.name().equals(report.getReportType())) {
            permissionService.requireProjectManage(actor, report.getProjectId());
        } else {
            permissionService.requireTeamWorkloadAnalyze(actor, report.getTeamId());
        }
    }

    private List<ReportSourceVO> visibleSources(Long actor, AiAnalysisReport report) {
        List<AiAnalysisReportSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<AiAnalysisReportSource>()
                        .eq(AiAnalysisReportSource::getReportId, report.getReportId())
                        .orderByAsc(AiAnalysisReportSource::getCitationId));
        Set<Long> taskIds = sources.stream().filter(value -> "TASK".equals(value.getSourceType()))
                .map(AiAnalysisReportSource::getSourceId).collect(java.util.stream.Collectors.toSet());
        Set<Long> reviewIds = sources.stream().filter(value -> "WEEKLY_REVIEW".equals(value.getSourceType()))
                .map(AiAnalysisReportSource::getSourceId).collect(java.util.stream.Collectors.toSet());
        Set<Long> readableTasks = permissionService.filterReadableTaskIds(actor, taskIds);
        Set<Long> readableReviews = permissionService.filterReadableWeeklyReviewIds(actor, reviewIds);
        return sources.stream().filter(value -> "TASK".equals(value.getSourceType())
                        ? readableTasks.contains(value.getSourceId()) : readableReviews.contains(value.getSourceId()))
                .map(value -> new ReportSourceVO(value.getCitationId(), value.getSourceType(),
                        value.getSourceId(), value.getTitleSnapshot())).toList();
    }

    private long currentVersion(AiAnalysisReport report) {
        return report.getProjectId() != null ? versionService.projectVersion(report.getProjectId())
                : versionService.teamVersion(report.getTeamId());
    }

    private AiAnalysisReport requireReport(String reportId) {
        if (reportId == null || reportId.isBlank() || reportId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "reportId 不合法");
        }
        AiAnalysisReport report = reportMapper.selectOne(new LambdaQueryWrapper<AiAnalysisReport>()
                .eq(AiAnalysisReport::getReportId, reportId.trim()).eq(AiAnalysisReport::getIsDelete, 0)
                .last("limit 1"));
        if (report == null) throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        return report;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) return null;
        try { return AnalysisReportTypeEnum.valueOf(type.trim()).name(); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.PARAMS_ERROR, "报告类型不合法"); }
    }

    private List<TeamMemberMetricSnapshot> readMetrics(String json) {
        try { return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {}); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告成员指标损坏"); }
    }

    private List<String> readRecommendations(String json) {
        try { return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {}); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告建议数据损坏"); }
    }

    private Long currentUserId() {
        Long actor = UserHolder.get();
        if (actor == null) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        return actor;
    }
}

