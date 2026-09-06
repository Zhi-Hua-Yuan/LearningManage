package com.spt.learningmanage.controller;

import com.spt.learningmanage.exception.GlobalExceptionHandler;
import com.spt.learningmanage.model.vo.agent.AgentRunCreatedVO;
import com.spt.learningmanage.model.vo.agent.AgentRunVO;
import com.spt.learningmanage.service.AgentReportService;
import com.spt.learningmanage.service.AgentRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTest {
    private final AgentRunService runService = mock(AgentRunService.class);
    private final AgentReportService reportService = mock(AgentReportService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AgentController(runService, reportService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void projectRiskSubmissionUsesStableAsyncContract() throws Exception {
        when(runService.submitProjectRisk(any())).thenReturn(new AgentRunCreatedVO("run-1", "PENDING"));
        mvc.perform(post("/ai/agent/project-risk").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10,\"clientRequestId\":\"request-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void invalidSubmissionFailsBeanValidation() throws Exception {
        mvc.perform(post("/ai/agent/team-workload").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":0,\"clientRequestId\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void getRunDelegatesOpaqueRunId() throws Exception {
        when(runService.getRun("run-1")).thenReturn(new AgentRunVO(
                "run-1", "PROJECT_RISK", "RUNNING", "TOOL:queryTaskStats",
                0, 4, "TOOL_CALLING", false, null, null, null, null, null, null));
        mvc.perform(get("/ai/agent/run/run-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
    }
}
