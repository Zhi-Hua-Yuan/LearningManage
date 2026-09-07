package com.spt.learningmanage.controller;

import com.spt.learningmanage.exception.GlobalExceptionHandler;
import com.spt.learningmanage.model.dto.ops.CleanupRunCreateRequest;
import com.spt.learningmanage.model.vo.ops.AiOpsOverviewVO;
import com.spt.learningmanage.model.vo.ops.CleanupRunVO;
import com.spt.learningmanage.service.AiOpsQueryService;
import com.spt.learningmanage.service.CleanupRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiOpsControllerTest {
    @Mock AiOpsQueryService ops;
    @Mock CleanupRunService cleanup;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AiOpsController(ops, cleanup))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void exposesSanitizedOverviewEndpoint() throws Exception {
        when(ops.overview(null, null)).thenReturn(new AiOpsOverviewVO());
        mvc.perform(get("/admin/ai/ops/overview"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void validatesAndSubmitsCleanupRequest() throws Exception {
        CleanupRunVO result = new CleanupRunVO();
        result.setRunId("cleanup_1");
        when(cleanup.submit(any(CleanupRunCreateRequest.class))).thenReturn(result);
        mvc.perform(post("/admin/ai/ops/cleanup-runs")
                        .contentType("application/json")
                        .content("{\"dryRun\":true,\"clientRequestId\":\"cleanup-request-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("cleanup_1"));
    }
}
