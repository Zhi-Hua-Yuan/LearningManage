package com.spt.learningmanage.integration;

import com.spt.learningmanage.constant.AgentRunStatusEnum;
import com.spt.learningmanage.job.AgentRunWorker;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.agent.AgentProjectRiskRequest;
import com.spt.learningmanage.model.dto.agent.AgentReportConfirmRequest;
import com.spt.learningmanage.model.dto.agent.AgentReportQueryRequest;
import com.spt.learningmanage.model.dto.agent.AgentTeamWorkloadRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.AgentReportService;
import com.spt.learningmanage.service.AgentRunService;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import com.spt.learningmanage.service.agent.AgentRunCompletion;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.scheduling.enabled=false",
        "ai.agent.enabled=true",
        "ai.agent.worker-enabled=false",
        "ai.agent.tool-calling-enabled=true",
        "ai.agent.overall-timeout-seconds=30",
        "ai.rag.enabled=false"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "STAGE6_AGENT_IT_ENABLED", matches = "true")
class AgentEndToEndIT {
    private static final long OWNER_ID = 2_096_600_000_000_001L;
    private static final long MEMBER_ID = 2_096_600_000_000_002L;
    private static final long PERSONAL_PROJECT_ID = 2_096_600_000_000_010L;
    private static final long TEAM_ID = 2_096_600_000_000_020L;
    private static final long TEAM_PROJECT_ID = 2_096_600_000_000_021L;

    @Autowired UserMapper userMapper;
    @Autowired TeamMapper teamMapper;
    @Autowired TeamMemberMapper teamMemberMapper;
    @Autowired ProjectMapper projectMapper;
    @Autowired TaskMapper taskMapper;
    @Autowired TaskCreationService taskCreationService;
    @Autowired AgentRunService runService;
    @Autowired AgentRunQueueService queueService;
    @Autowired AgentRunWorker worker;
    @Autowired AgentReportService reportService;
    @Autowired BusinessDataVersionService versionService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
        userMapper.insert(user(OWNER_ID, "stage6_agent_owner"));
        userMapper.insert(user(MEMBER_ID, "stage6_agent_member"));
        projectMapper.insert(project(PERSONAL_PROJECT_ID, OWNER_ID, null, "Agent project risk"));
        taskCreationService.createTask(task(PERSONAL_PROJECT_ID, "逾期验收任务", LocalDate.now().minusDays(2)),
                new ProjectAccessScope(OWNER_ID, PERSONAL_PROJECT_ID, OWNER_ID, null, null), OWNER_ID);
        taskCreationService.createTask(task(PERSONAL_PROJECT_ID, "近期验收任务", LocalDate.now().plusDays(3)),
                new ProjectAccessScope(OWNER_ID, PERSONAL_PROJECT_ID, OWNER_ID, null, null), OWNER_ID);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanup();
    }

    @Test
    void projectRiskRunsToolsCreatesDraftAndConfirmsExactlyOneReport() {
        UserHolder.set(OWNER_ID);
        AgentProjectRiskRequest request = new AgentProjectRiskRequest();
        request.setProjectId(PERSONAL_PROJECT_ID);
        request.setClientRequestId("stage6-agent-it-risk");
        var submitted = runService.submitProjectRisk(request);
        assertEquals(submitted.runId(), runService.submitProjectRisk(request).runId());

        var claimed = queueService.claimReady("stage6-agent-it", 10);
        assertEquals(1, claimed.size());
        worker.process(claimed.get(0));

        var run = runService.getRun(submitted.runId());
        assertEquals(AgentRunStatusEnum.SUCCEEDED.name(), run.status());
        assertEquals("TOOL_CALLING", run.orchestrationMode());
        assertEquals(2, run.completedToolCount());
        assertNotNull(run.draftId());

        AgentReportConfirmRequest confirm = new AgentReportConfirmRequest();
        confirm.setDraftId(run.draftId());
        confirm.setOperationId("stage6-agent-it-confirm");
        var first = reportService.confirm(confirm);
        var replay = reportService.confirm(confirm);
        assertFalse(first.getIdempotentReplay());
        assertTrue(replay.getIdempotentReplay());
        assertEquals(first.getBusinessId(), replay.getBusinessId());

        AgentReportQueryRequest query = new AgentReportQueryRequest();
        query.setReportType("PROJECT_RISK");
        var page = reportService.list(query);
        assertEquals(1, page.getTotal());
        assertEquals("ACTIVE", page.getRecords().get(0).status());
        assertFalse(page.getRecords().get(0).summary().isBlank());
        assertEquals(1L, reportCount());
        assertEquals(2L, toolLogCount());
    }

    @Test
    void teamWorkloadUsesFixedWorkflowAndMemberNeverReceivesManagerMetrics() {
        Team team = new Team();
        team.setId(TEAM_ID);
        team.setName("Stage 6 team");
        team.setOwnerId(OWNER_ID);
        team.setInviteCode("S6AGENT1");
        team.setIsDelete(0);
        teamMapper.insert(team);
        teamMemberMapper.insert(member(TEAM_ID, OWNER_ID, "OWNER"));
        projectMapper.insert(project(TEAM_PROJECT_ID, OWNER_ID, TEAM_ID, "Team workload"));
        taskCreationService.createTask(task(TEAM_PROJECT_ID, "团队逾期任务", LocalDate.now().minusDays(1)),
                new ProjectAccessScope(OWNER_ID, TEAM_PROJECT_ID, OWNER_ID, TEAM_ID,
                        com.spt.learningmanage.constant.TeamRoleEnum.OWNER), OWNER_ID);

        UserHolder.set(OWNER_ID);
        AgentTeamWorkloadRequest request = new AgentTeamWorkloadRequest();
        request.setTeamId(TEAM_ID);
        request.setClientRequestId("stage6-agent-it-team");
        var submitted = runService.submitTeamWorkload(request);
        var claimed = queueService.claimReady("stage6-agent-it", 10);
        assertEquals(1, claimed.size());
        worker.process(claimed.get(0));
        var run = runService.getRun(submitted.runId());
        assertEquals("SUCCEEDED", run.status());
        assertEquals("FIXED_WORKFLOW", run.orchestrationMode());

        AgentReportConfirmRequest confirm = new AgentReportConfirmRequest();
        confirm.setDraftId(run.draftId());
        confirm.setOperationId("stage6-agent-it-team-confirm");
        reportService.confirm(confirm);
        AgentReportQueryRequest query = new AgentReportQueryRequest();
        query.setReportType("TEAM_WORKLOAD");
        var ownerReport = reportService.list(query).getRecords().get(0);
        assertTrue(ownerReport.memberMetrics().containsKey("members"));

        teamMemberMapper.insert(member(TEAM_ID, MEMBER_ID, "MEMBER"));
        versionService.incrementTeam(TEAM_ID);
        UserHolder.set(MEMBER_ID);
        var memberReport = reportService.get(ownerReport.reportId());
        assertEquals("STALE", memberReport.status());
        assertFalse(memberReport.memberMetrics().containsKey("members"));
        assertFalse(memberReport.summary().equals(ownerReport.summary()));
    }

    @Test
    void pendingAndRunningCancellationHaveSingleCanceledTerminal() {
        UserHolder.set(OWNER_ID);
        AgentProjectRiskRequest pendingRequest = riskRequest("stage6-agent-it-cancel-pending");
        var pending = runService.submitProjectRisk(pendingRequest);
        assertEquals("CANCELED", runService.cancel(pending.runId()).status());
        assertEquals("CANCELED", runService.getRun(pending.runId()).status());

        AgentProjectRiskRequest runningRequest = riskRequest("stage6-agent-it-cancel-running");
        var running = runService.submitProjectRisk(runningRequest);
        var claimed = queueService.claimReady("stage6-agent-it", 10);
        assertEquals(1, claimed.size());
        assertTrue(runService.cancel(running.runId()).cancelRequested());
        worker.process(claimed.get(0));
        assertEquals("CANCELED", runService.getRun(running.runId()).status());
    }

    @Test
    void staleWorkerIsFencedAndChangedDataPreventsReportConfirmation() {
        UserHolder.set(OWNER_ID);
        var staleRun = runService.submitProjectRisk(riskRequest("stage6-agent-it-stale-worker"));
        var firstClaim = queueService.claimReady("worker-a", 1).get(0);
        jdbcTemplate.update("UPDATE ai_agent_run SET lease_until=DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE run_id=?",
                staleRun.runId());
        var secondClaim = queueService.claimReady("worker-b", 1).get(0);
        assertNotEquals(firstClaim.getExecutionToken(), secondClaim.getExecutionToken());
        assertFalse(queueService.complete(firstClaim, new AgentRunCompletion(
                "FAILED", "OLD_WORKER", null, null, null, null,
                "OLD_WORKER", "must be fenced", null, null, null)));
        assertTrue(queueService.complete(secondClaim, new AgentRunCompletion(
                "FAILED", "TEST_COMPLETE", null, null, null, null,
                "TEST", "closed by integration test", null, null, null)));

        var submitted = runService.submitProjectRisk(riskRequest("stage6-agent-it-stale-draft"));
        var claim = queueService.claimReady("stage6-agent-it", 1).get(0);
        worker.process(claim);
        var completed = runService.getRun(submitted.runId());
        assertNotNull(completed.draftId());
        versionService.incrementProject(PERSONAL_PROJECT_ID);
        AgentReportConfirmRequest confirm = new AgentReportConfirmRequest();
        confirm.setDraftId(completed.draftId());
        confirm.setOperationId("stage6-agent-it-stale-confirm");
        var exception = assertThrows(com.spt.learningmanage.exception.BusinessException.class,
                () -> reportService.confirm(confirm));
        assertEquals(com.spt.learningmanage.exception.ErrorCode.AGENT_REPORT_STALE,
                exception.getErrorCode());
    }

    private AgentProjectRiskRequest riskRequest(String clientRequestId) {
        AgentProjectRiskRequest request = new AgentProjectRiskRequest();
        request.setProjectId(PERSONAL_PROJECT_ID);
        request.setClientRequestId(clientRequestId);
        return request;
    }

    private User user(long id, String account) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setUsername(account);
        user.setPassword("not-a-real-password-hash");
        user.setUserRole("USER");
        user.setIsDelete(0);
        return user;
    }

    private TeamMember member(long teamId, long userId, String role) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        member.setIsDelete(0);
        return member;
    }

    private Project project(long id, long ownerId, Long teamId, String name) {
        Project project = new Project();
        project.setId(id);
        project.setUserId(ownerId);
        project.setTeamId(teamId);
        project.setName(name);
        project.setStatus(0);
        project.setOrderNo(0);
        project.setProgress(java.math.BigDecimal.ZERO);
        project.setIsDelete(0);
        return project;
    }

    private Task task(long projectId, String title, LocalDate dueDate) {
        Task task = new Task();
        task.setProjectId(projectId);
        task.setTitle(title);
        task.setDescription("Stage 6 deterministic Agent integration fixture");
        task.setStatus(0);
        task.setPriority(3);
        task.setDueDate(dueDate);
        task.setDeleteSource(0);
        task.setIsDelete(0);
        return task;
    }

    private long reportCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_analysis_report WHERE creator_user_id=?", Long.class, OWNER_ID);
    }

    private long toolLogCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_agent_tool_log "
                + "WHERE run_id IN (SELECT run_id FROM ai_agent_run WHERE user_id=?)", Long.class, OWNER_ID);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_analysis_report_source WHERE report_id IN (SELECT report_id FROM ai_analysis_report WHERE creator_user_id=?)", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_analysis_report WHERE creator_user_id=?", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_draft_confirm_log WHERE user_id IN (?,?)", OWNER_ID, MEMBER_ID);
        jdbcTemplate.update("DELETE FROM ai_draft WHERE user_id IN (?,?)", OWNER_ID, MEMBER_ID);
        jdbcTemplate.update("DELETE FROM ai_agent_tool_log WHERE run_id IN (SELECT run_id FROM ai_agent_run WHERE user_id IN (?,?))", OWNER_ID, MEMBER_ID);
        jdbcTemplate.update("DELETE FROM ai_call_log WHERE user_id IN (?,?)", OWNER_ID, MEMBER_ID);
        jdbcTemplate.update("DELETE FROM ai_agent_run WHERE user_id IN (?,?)", OWNER_ID, MEMBER_ID);
        jdbcTemplate.update("DELETE FROM ai_knowledge_index_event WHERE source_id >= 2096600000000000");
        jdbcTemplate.update("DELETE FROM task_assignment_log WHERE task_id IN (SELECT id FROM task WHERE project_id IN (?,?))", PERSONAL_PROJECT_ID, TEAM_PROJECT_ID);
        jdbcTemplate.update("DELETE FROM task WHERE project_id IN (?,?)", PERSONAL_PROJECT_ID, TEAM_PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id IN (?,?)", PERSONAL_PROJECT_ID, TEAM_PROJECT_ID);
        jdbcTemplate.update("DELETE FROM team_member WHERE team_id=?", TEAM_ID);
        jdbcTemplate.update("DELETE FROM team WHERE id=?", TEAM_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id IN (?,?)", OWNER_ID, MEMBER_ID);
    }
}
