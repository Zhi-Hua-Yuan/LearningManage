package com.spt.learningmanage.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spt.learningmanage.exception.GlobalExceptionHandler;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.interceptor.LoginInterceptor;
import com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest;
import com.spt.learningmanage.model.vo.team.TeamMembershipTerminationVO;
import com.spt.learningmanage.service.JwtTokenService;
import com.spt.learningmanage.service.TeamMembershipTerminationService;
import com.spt.learningmanage.service.TeamService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamMembershipTerminationControllerTest {

    private static final String CONTEXT_PATH = "/api";
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private MockMvc mockMvc;
    private TeamMembershipTerminationService terminationService;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        TeamController controller = new TeamController();
        terminationService = org.mockito.Mockito.mock(TeamMembershipTerminationService.class);
        TeamService teamService = org.mockito.Mockito.mock(TeamService.class);
        jwtTokenService = org.mockito.Mockito.mock(JwtTokenService.class);
        ReflectionTestUtils.setField(controller, "teamService", teamService);
        ReflectionTestUtils.setField(controller, "teamMembershipTerminationService", terminationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new LoginInterceptor(jwtTokenService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        lenient().when(jwtTokenService.parseToken("valid-token")).thenReturn(11L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void leaveReturnsFrozenResultShape() throws Exception {
        when(terminationService.leaveTeam(7L)).thenReturn(vo("MEMBER_LEFT", 11L, 2));

        MvcResult result = mockMvc.perform(post(CONTEXT_PATH + "/team/7/leave")
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.teamId").value(7))
                .andExpect(jsonPath("$.data.memberUserId").value(11))
                .andExpect(jsonPath("$.data.action").value("MEMBER_LEFT"))
                .andExpect(jsonPath("$.data.unassignedTaskCount").value(2))
                .andExpect(jsonPath("$.data.terminatedAt").value("2026-08-29T10:30:00"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(Set.of("teamId", "memberUserId", "action", "unassignedTaskCount", "terminatedAt"),
                fieldNames(data));
        verify(terminationService).leaveTeam(7L);
    }

    @Test
    void removeBindsRequestAndReturnsRemovedAction() throws Exception {
        when(terminationService.removeMember(any(TeamMemberRemoveRequest.class)))
                .thenReturn(vo("MEMBER_REMOVED", 22L, 1));
        String body = "{\"teamId\":7,\"targetUserId\":22}";

        mockMvc.perform(post(CONTEXT_PATH + "/team/member/remove")
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("MEMBER_REMOVED"))
                .andExpect(jsonPath("$.data.memberUserId").value(22));
        verify(terminationService).removeMember(any(TeamMemberRemoveRequest.class));
    }

    @Test
    void noTokenAndForbiddenResponsesDoNotLeakTargetState() throws Exception {
        mockMvc.perform(post(CONTEXT_PATH + "/team/7/leave")
                .contextPath(CONTEXT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.data").value((Object) null));
        verify(terminationService, never()).leaveTeam(any());

        when(terminationService.leaveTeam(7L)).thenThrow(new PermissionDeniedException());
        MvcResult forbidden = mockMvc.perform(post(CONTEXT_PATH + "/team/7/leave")
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.data").value((Object) null))
                .andReturn();
        JsonNode response = objectMapper.readTree(forbidden.getResponse().getContentAsString());
        assertEquals(Set.of("code", "message", "data"), fieldNames(response));
        assertNull(UserHolder.get());
    }

    @Test
    void routesRemainPostOnlyAndFrozen() throws Exception {
        org.springframework.web.bind.annotation.RequestMapping mapping =
                TeamController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        java.lang.reflect.Method leave = TeamController.class.getDeclaredMethod("leaveTeam", Long.class);
        java.lang.reflect.Method remove = TeamController.class.getDeclaredMethod("removeMember", TeamMemberRemoveRequest.class);
        assertArrayEquals(new String[]{"/team"}, mapping.value());
        assertArrayEquals(new String[]{"/{teamId}/leave"},
                leave.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value());
        assertArrayEquals(new String[]{"/member/remove"},
                remove.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value());
    }

    private TeamMembershipTerminationVO vo(String action, Long memberUserId, int count) {
        TeamMembershipTerminationVO vo = new TeamMembershipTerminationVO();
        vo.setTeamId(7L);
        vo.setMemberUserId(memberUserId);
        vo.setAction(action);
        vo.setUnassignedTaskCount(count);
        vo.setTerminatedAt(LocalDateTime.of(2026, 8, 29, 10, 30));
        return vo;
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new LinkedHashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(fields::add);
        return fields;
    }
}
