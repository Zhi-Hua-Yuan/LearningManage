package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.model.dto.milestone.MilestoneCreateRequest;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MilestoneOrderingServiceImplTest {

    @Mock private MilestoneMapper milestoneMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private PermissionService permissionService;
    @InjectMocks private MilestoneServiceImpl service;

    @AfterEach
    void cleanup() {
        UserHolder.remove();
    }

    @Test
    void createUsesHistoricalMaximumIncludingDeletedRows() {
        UserHolder.set(7L);
        Project project = new Project();
        project.setId(10L);
        when(projectMapper.selectActiveByIdForUpdate(10L)).thenReturn(project);
        when(milestoneMapper.selectMaxOrderNoIncludingDeleted(10L)).thenReturn(2);
        when(milestoneMapper.insert(any(Milestone.class))).thenAnswer(invocation -> {
            Milestone milestone = invocation.getArgument(0);
            milestone.setId(20L);
            return 1;
        });
        MilestoneCreateRequest request = new MilestoneCreateRequest();
        request.setProjectId(10L);
        request.setName("replacement");

        assertEquals(20L, service.create(request));

        ArgumentCaptor<Milestone> captor = ArgumentCaptor.forClass(Milestone.class);
        verify(milestoneMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getOrderNo());
    }
}
