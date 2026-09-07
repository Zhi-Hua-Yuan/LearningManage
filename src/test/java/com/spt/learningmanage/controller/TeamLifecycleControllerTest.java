package com.spt.learningmanage.controller;

import com.spt.learningmanage.model.dto.team.TeamOwnerTransferRequest;
import com.spt.learningmanage.model.dto.team.TeamUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TeamLifecycleControllerTest {

    @Test
    void lifecycleRoutesRemainExplicitAndStable() throws Exception {
        assertPost("updateTeam", new Class<?>[]{TeamUpdateRequest.class}, "/update");
        assertPost("regenerateInvite", new Class<?>[]{Long.class}, "/{teamId}/invite/regenerate");
        assertPost("transferOwnership", new Class<?>[]{TeamOwnerTransferRequest.class}, "/owner/transfer");
        assertPost("dissolveTeam", new Class<?>[]{Long.class}, "/{teamId}/dissolve");
        assertGet("getInvite", new Class<?>[]{Long.class}, "/{teamId}/invite");
        assertGet("checkDissolution", new Class<?>[]{Long.class}, "/{teamId}/dissolution-check");
    }

    private void assertPost(String method, Class<?>[] parameters, String path) throws Exception {
        assertArrayEquals(new String[]{path}, TeamController.class.getDeclaredMethod(method, parameters)
                .getAnnotation(PostMapping.class).value());
    }

    private void assertGet(String method, Class<?>[] parameters, String path) throws Exception {
        assertArrayEquals(new String[]{path}, TeamController.class.getDeclaredMethod(method, parameters)
                .getAnnotation(GetMapping.class).value());
    }
}
