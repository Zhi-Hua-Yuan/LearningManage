package com.spt.learningmanage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.exception.GlobalExceptionHandler;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;
import com.spt.learningmanage.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RagControllerTest {
    private final RagService service = mock(RagService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RagController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void askUsesStableContractAndBeanValidation() throws Exception {
        RagAnswerVO answer = new RagAnswerVO();
        answer.setRequestId("request-1");
        answer.setStatus("ACTIVE");
        when(service.ask(any())).thenReturn(answer);

        mvc.perform(post("/ai/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"为什么延期\",\"projectId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requestId").value("request-1"));
        verify(service).ask(any());

        mvc.perform(post("/ai/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\",\"projectId\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void getResultDelegatesOpaqueRequestId() throws Exception {
        RagAnswerVO answer = new RagAnswerVO();
        answer.setRequestId("request-1");
        when(service.getResult("request-1")).thenReturn(answer);

        mvc.perform(get("/ai/rag/result/request-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value("request-1"));
    }
}
