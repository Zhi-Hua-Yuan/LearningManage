package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.exception.GlobalExceptionHandler;
import com.spt.learningmanage.interceptor.AiRateLimitInterceptor;
import com.spt.learningmanage.interceptor.LoginInterceptor;
import com.spt.learningmanage.model.dto.task.TaskAssignmentHistoryQueryRequest;
import com.spt.learningmanage.model.vo.task.AssignmentUserSummaryVO;
import com.spt.learningmanage.model.vo.task.TaskAssignmentHistoryVO;
import com.spt.learningmanage.service.JwtTokenService;
import com.spt.learningmanage.service.RateLimitService;
import com.spt.learningmanage.service.TaskAssignmentService;
import com.spt.learningmanage.service.TaskService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentHistoryControllerTest {

    private static final String CONTEXT_PATH = "/api";
    private static final String HISTORY_PATH =
            CONTEXT_PATH + "/task/62001/assignment-history";

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private TaskService taskService;

    @Mock
    private TaskAssignmentService taskAssignmentService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        TaskController controller = new TaskController();
        ReflectionTestUtils.setField(controller, "taskService", taskService);
        ReflectionTestUtils.setField(
                controller, "taskAssignmentService", taskAssignmentService);

        LoginInterceptor loginInterceptor = new LoginInterceptor(jwtTokenService);
        AiRateLimitInterceptor aiRateLimitInterceptor = new AiRateLimitInterceptor();
        ReflectionTestUtils.setField(
                aiRateLimitInterceptor, "rateLimitService", rateLimitService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(loginInterceptor, aiRateLimitInterceptor)
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        lenient().when(jwtTokenService.parseToken("valid-token")).thenReturn(9L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void shouldBindExplicitPaginationAndReturnFrozenResponseShape() throws Exception {
        Page<TaskAssignmentHistoryVO> page = new Page<>(3, 20, 41);
        page.setRecords(List.of(historyVo()));
        when(taskAssignmentService.listAssignmentHistory(
                argThat(request -> request.getTaskId().equals(62001L)
                        && request.getCurrent().equals(3L)
                        && request.getSize().equals(20L))))
                .thenReturn(page);

        MvcResult result = mockMvc.perform(authenticatedGet(HISTORY_PATH)
                        .queryParam("current", "3")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.current").value(3))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(41))
                .andExpect(jsonPath("$.data.pages").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(101))
                .andExpect(jsonPath("$.data.records[0].taskId").value(62001))
                .andExpect(jsonPath("$.data.records[0].action").value("REASSIGN"))
                .andExpect(jsonPath("$.data.records[0].fromAssignee.userId").value(11))
                .andExpect(jsonPath("$.data.records[0].fromAssignee.username").value("alice"))
                .andExpect(jsonPath("$.data.records[0].toAssignee.userId").value(12))
                .andExpect(jsonPath("$.data.records[0].toAssignee.username").value("bob"))
                .andExpect(jsonPath("$.data.records[0].assignedBy.userId").value(3))
                .andExpect(jsonPath("$.data.records[0].assignedBy.username").value("owner"))
                .andExpect(jsonPath("$.data.records[0].reason").value("调整负责人"))
                .andExpect(jsonPath("$.data.records[0].createTime")
                        .value("2026-08-29T10:30:00"))
                .andReturn();

        JsonNode record = objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/records/0");
        assertEquals(Set.of("id", "taskId", "action", "fromAssignee", "toAssignee",
                "assignedBy", "reason", "createTime"), fieldNames(record));
        assertEquals(Set.of("userId", "username"), fieldNames(record.get("fromAssignee")));
        assertEquals(Set.of("userId", "username"), fieldNames(record.get("toAssignee")));
        assertEquals(Set.of("userId", "username"), fieldNames(record.get("assignedBy")));

        ArgumentCaptor<TaskAssignmentHistoryQueryRequest> captor =
                ArgumentCaptor.forClass(TaskAssignmentHistoryQueryRequest.class);
        verify(taskAssignmentService).listAssignmentHistory(captor.capture());
        assertEquals(62001L, captor.getValue().getTaskId());
        assertEquals(3L, captor.getValue().getCurrent());
        assertEquals(20L, captor.getValue().getSize());
        assertNull(UserHolder.get());
    }

    @Test
    void shouldApplyFrozenPaginationDefaults() throws Exception {
        when(taskAssignmentService.listAssignmentHistory(
                argThat(request -> request.getTaskId().equals(62001L)
                        && request.getCurrent().equals(1L)
                        && request.getSize().equals(50L))))
                .thenReturn(new Page<>(1, 50, 0));

        mockMvc.perform(authenticatedGet(HISTORY_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.total").value(0));

        ArgumentCaptor<TaskAssignmentHistoryQueryRequest> captor =
                ArgumentCaptor.forClass(TaskAssignmentHistoryQueryRequest.class);
        verify(taskAssignmentService).listAssignmentHistory(captor.capture());
        assertEquals(1L, captor.getValue().getCurrent());
        assertEquals(50L, captor.getValue().getSize());
    }

    @Test
    void shouldPreserveUnassignedAndDeletedUserNullSemantics() throws Exception {
        TaskAssignmentHistoryVO vo = historyVo();
        vo.setFromAssignee(null);
        vo.getToAssignee().setUsername(null);
        Page<TaskAssignmentHistoryVO> page = new Page<>(1, 50, 1);
        page.setRecords(List.of(vo));
        when(taskAssignmentService.listAssignmentHistory(
                argThat(request -> request.getTaskId().equals(62001L))))
                .thenReturn(page);

        mockMvc.perform(authenticatedGet(HISTORY_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].fromAssignee").value((Object) null))
                .andExpect(jsonPath("$.data.records[0].toAssignee.userId").value(12))
                .andExpect(jsonPath("$.data.records[0].toAssignee.username")
                        .value((Object) null));
    }

    @Test
    void shouldRejectRequestWithoutTokenBeforeServiceInvocation() throws Exception {
        mockMvc.perform(get(HISTORY_PATH).contextPath(CONTEXT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.data").value((Object) null));

        verify(taskAssignmentService, never()).listAssignmentHistory(
                org.mockito.ArgumentMatchers.any());
        assertNull(UserHolder.get());
    }

    @Test
    void shouldRejectInvalidTokenBeforeServiceInvocation() throws Exception {
        when(jwtTokenService.parseToken("invalid-token")).thenReturn(null);

        mockMvc.perform(get(HISTORY_PATH)
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.data").value((Object) null));

        verify(taskAssignmentService, never()).listAssignmentHistory(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnFixedForbiddenResponseWithoutHistoryLeakage() throws Exception {
        when(taskAssignmentService.listAssignmentHistory(
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new PermissionDeniedException());

        MvcResult result = mockMvc.perform(authenticatedGet(HISTORY_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.message").value("无权限执行该操作"))
                .andExpect(jsonPath("$.data").value((Object) null))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(Set.of("code", "message", "data"), fieldNames(response));
    }

    @Test
    void shouldPreserveBusinessValidationHttpCompatibility() throws Exception {
        when(taskAssignmentService.listAssignmentHistory(
                argThat(request -> request.getCurrent().equals(0L))))
                .thenThrow(new BusinessException(
                        ErrorCode.PARAMS_ERROR, "current 必须大于等于1"));

        mockMvc.perform(authenticatedGet(HISTORY_PATH).queryParam("current", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("current 必须大于等于1"))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    void shouldReturnBadRequestForTypeMismatchWithoutCallingService() throws Exception {
        mockMvc.perform(authenticatedGet(HISTORY_PATH).queryParam("current", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("参数类型错误"))
                .andExpect(jsonPath("$.data").value((Object) null));

        verify(taskAssignmentService, never()).listAssignmentHistory(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnBadRequestForOverflowingNumberWithoutCallingService() throws Exception {
        mockMvc.perform(authenticatedGet(HISTORY_PATH)
                        .queryParam("size", "999999999999999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("参数类型错误"));

        verify(taskAssignmentService, never()).listAssignmentHistory(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldExposeOnlyFrozenGetRoute() throws Exception {
        RequestMapping controllerMapping = TaskController.class
                .getAnnotation(RequestMapping.class);
        Method method = TaskController.class.getDeclaredMethod(
                "listAssignmentHistory", Long.class, Long.class, Long.class);
        GetMapping getMapping = method.getAnnotation(GetMapping.class);

        assertArrayEquals(new String[]{"/task"}, controllerMapping.value());
        assertArrayEquals(
                new String[]{"/{taskId}/assignment-history"}, getMapping.value());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    authenticatedGet(String path) {
        return get(path)
                .contextPath(CONTEXT_PATH)
                .header("Authorization", "Bearer valid-token");
    }

    private TaskAssignmentHistoryVO historyVo() {
        TaskAssignmentHistoryVO vo = new TaskAssignmentHistoryVO();
        vo.setId(101L);
        vo.setTaskId(62001L);
        vo.setAction("REASSIGN");
        vo.setFromAssignee(user(11L, "alice"));
        vo.setToAssignee(user(12L, "bob"));
        vo.setAssignedBy(user(3L, "owner"));
        vo.setReason("调整负责人");
        vo.setCreateTime(LocalDateTime.of(2026, 8, 29, 10, 30));
        return vo;
    }

    private AssignmentUserSummaryVO user(Long userId, String username) {
        AssignmentUserSummaryVO user = new AssignmentUserSummaryVO();
        user.setUserId(userId);
        user.setUsername(username);
        return user;
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new LinkedHashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(fields::add);
        return fields;
    }
}
