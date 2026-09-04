package com.spt.learningmanage.service.impl.ai.draft;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.model.dto.ai.draft.TaskBreakdownConfirmationContext;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskCreationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskBreakdownDraftHandlerTest {

    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final MilestoneMapper milestoneMapper = mock(MilestoneMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final TaskCreationService taskCreationService = mock(TaskCreationService.class);
    private final TaskBreakdownDraftHandler handler = new TaskBreakdownDraftHandler(
            projectMapper, milestoneMapper, permissionService, taskCreationService);

    @Test
    void appliesPersistedDraftThroughSharedTaskCreationPath() {
        AiDraft draft = draft("""
                {"target":"通过考试","description":"目标描述","milestones":[
                  {"name":"准备阶段","tasks":[
                    {"name":"制定计划","priority":2,"dueDate":"2026-09-10"}
                  ]}
                ]}
                """);
        when(projectMapper.selectOne(any())).thenReturn(null);
        when(projectMapper.insert(any(Project.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Project.class).setId(101L);
            return 1;
        });
        when(milestoneMapper.insert(any(Milestone.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Milestone.class).setId(201L);
            return 1;
        });
        ProjectAccessScope scope = new ProjectAccessScope(1L, 101L, 1L, null, null);
        when(permissionService.requireProjectCreateTask(1L, 101L)).thenReturn(scope);

        Long projectId = handler.apply(draft, new TaskBreakdownConfirmationContext(null, null));

        assertEquals(101L, projectId);
        verify(permissionService).requireProjectCreateTask(1L, 101L);
        verify(taskCreationService).createTask(any(), any(), isNull());
    }

    @Test
    void rejectsDamagedPayloadBeforeBusinessWrite() {
        AiDraft draft = draft("not-json");

        assertThrows(BusinessException.class,
                () -> handler.apply(draft, new TaskBreakdownConfirmationContext(null, null)));
    }

    private AiDraft draft(String payload) {
        AiDraft draft = new AiDraft();
        draft.setUserId(1L);
        draft.setPayloadJson(payload);
        return draft;
    }
}
