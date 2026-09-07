package com.spt.learningmanage.integration;

import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.job.AgentRunWorker;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.agent.AgentProjectRiskRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.AgentRunService;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.scheduling.enabled=false",
        "ai.agent.enabled=true",
        "ai.agent.worker-enabled=false",
        "ai.agent.tool-calling-enabled=true",
        "ai.agent.overall-timeout-seconds=120",
        "ai.agent.tool-timeout-seconds=30",
        "ai.rag.enabled=false"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "STAGE6_REAL_AGENT_IT_ENABLED", matches = "true")
class RealAgentProviderValidationIT {
    private static final long OWNER_ID = 2_096_610_000_000_001L;
    private static final long PROJECT_ID = 2_096_610_000_000_010L;

    @Autowired UserMapper userMapper;
    @Autowired ProjectMapper projectMapper;
    @Autowired TaskCreationService taskCreationService;
    @Autowired AgentRunService runService;
    @Autowired AgentRunQueueService queueService;
    @Autowired AgentRunWorker worker;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        cleanup();
        User user = new User();
        user.setId(OWNER_ID);
        user.setAccount("stage6_real_agent_owner");
        user.setUsername("stage6_real_agent_owner");
        user.setPassword("not-a-real-password-hash");
        user.setUserRole("USER");
        user.setIsDelete(0);
        userMapper.insert(user);

        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setUserId(OWNER_ID);
        project.setName("Stage 6 real Qwen Agent validation");
        project.setStatus(0);
        project.setOrderNo(0);
        project.setProgress(java.math.BigDecimal.ZERO);
        project.setIsDelete(0);
        projectMapper.insert(project);

        taskCreationService.createTask(task("逾期交付任务", LocalDate.now().minusDays(3)), scope(), OWNER_ID);
        taskCreationService.createTask(task("本周到期任务", LocalDate.now().plusDays(2)), scope(), OWNER_ID);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanup();
    }

    @Test
    void realQwenCompletesControlledProjectRiskToolCallingPath() {
        UserHolder.set(OWNER_ID);
        AgentProjectRiskRequest request = new AgentProjectRiskRequest();
        request.setProjectId(PROJECT_ID);
        request.setClientRequestId("stage6-real-agent-provider-validation");
        var submitted = runService.submitProjectRisk(request);

        var claimed = queueService.claimReady("stage6-real-agent-validator", 1);
        assertEquals(1, claimed.size());
        worker.process(claimed.get(0));

        var run = runService.getRun(submitted.runId());
        assertEquals("SUCCEEDED", run.status(), () -> "真实 Agent 未成功：" + run.failureType());
        assertEquals("TOOL_CALLING", run.orchestrationMode());
        assertNotNull(run.draftId());
        assertTrue(run.completedToolCount() >= 2 && run.completedToolCount() <= 4);

        List<String> successfulTools = jdbcTemplate.queryForList(
                "SELECT tool_name FROM ai_agent_tool_log WHERE run_id=? AND status='SUCCEEDED'",
                String.class, submitted.runId());
        assertTrue(successfulTools.contains("queryTaskStats"));
        assertTrue(successfulTools.contains("queryOverdueTasks"));
        assertFalse(successfulTools.contains("deleteTask"));

        Integer callCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_log WHERE agent_run_id=? AND status=? "
                        + "AND provider_request_id IS NOT NULL AND total_tokens > 0",
                Integer.class, submitted.runId(), AiCallLogStatusEnum.SUCCESS.getValue());
        assertNotNull(callCount);
        assertTrue(callCount >= 2, "真实 Tool Calling 至少需要工具请求轮次和最终分析轮次");
        assertTrue(meterRegistry.get("learning.ai.invocations").counter().count() >= 2);
        assertTrue(meterRegistry.get("learning.agent.runs")
                .tag("status", "succeeded").counter().count() >= 1);
    }

    private ProjectAccessScope scope() {
        return new ProjectAccessScope(OWNER_ID, PROJECT_ID, OWNER_ID, null, null);
    }

    private Task task(String title, LocalDate dueDate) {
        Task task = new Task();
        task.setProjectId(PROJECT_ID);
        task.setTitle(title);
        task.setDescription("Stage 6 protected real-provider validation fixture");
        task.setStatus(0);
        task.setPriority(3);
        task.setDueDate(dueDate);
        task.setDeleteSource(0);
        task.setIsDelete(0);
        return task;
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_analysis_report_source WHERE report_id IN "
                + "(SELECT report_id FROM ai_analysis_report WHERE creator_user_id=?)", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_analysis_report WHERE creator_user_id=?", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_draft_confirm_log WHERE user_id=?", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_draft WHERE user_id=?", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_agent_tool_log WHERE run_id IN "
                + "(SELECT run_id FROM ai_agent_run WHERE user_id=?)", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_call_log WHERE user_id=?", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_agent_run WHERE user_id=?", OWNER_ID);
        jdbcTemplate.update("DELETE FROM ai_knowledge_index_event WHERE source_id >= 2096610000000000");
        jdbcTemplate.update("DELETE FROM task_assignment_log WHERE task_id IN "
                + "(SELECT id FROM task WHERE project_id=?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM task WHERE project_id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id=?", OWNER_ID);
    }
}
