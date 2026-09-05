package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeBackfillVO;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeIndexStatusVO;
import com.spt.learningmanage.service.KnowledgeAdminService;
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

class KnowledgeAdminControllerTest {

    @Test
    void statusAndEventListKeepStandardResponseContract() throws Exception {
        KnowledgeAdminService service = mock(KnowledgeAdminService.class);
        when(service.status()).thenReturn(new KnowledgeIndexStatusVO());
        when(service.listEvents(any())).thenReturn(new Page<>());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new KnowledgeAdminController(service)).build();

        mvc.perform(get("/admin/ai/knowledge/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/admin/ai/knowledge/events").param("status", "DEAD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void backfillCreationUsesFrozenRequestAndResponseShape() throws Exception {
        KnowledgeAdminService service = mock(KnowledgeAdminService.class);
        KnowledgeBackfillVO result = new KnowledgeBackfillVO();
        result.setRunId(7L);
        result.setStatus("PENDING");
        when(service.createBackfill(any())).thenReturn(result);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new KnowledgeAdminController(service)).build();

        mvc.perform(post("/admin/ai/knowledge/backfills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(java.util.Map.of(
                                "runKey", "stage4-initial-v1",
                                "runType", "INITIAL",
                                "sourceScope", "ALL",
                                "batchSize", 500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runId").value(7))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
