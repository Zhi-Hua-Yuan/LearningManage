package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TeamWriteLockService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectRecoveryTeamLockTest {

    @Mock private ProjectMapper projectMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private MilestoneMapper milestoneMapper;
    @Mock private PermissionService permissionService;
    @Mock private TeamWriteLockService teamWriteLockService;
    @InjectMocks private ProjectServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(Project.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), Project.class);
        }
    }

    @BeforeEach
    void setUp() {
        UserHolder.set(11L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void teamProjectRecoveryLocksActiveTeamBeforeRestoringProject() {
        Project deleted = deletedTeamProject();
        when(projectMapper.selectDeletedById(9L)).thenReturn(deleted);
        when(projectMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.recover(9L);

        InOrder order = inOrder(projectMapper, teamWriteLockService);
        order.verify(projectMapper).selectDeletedById(9L);
        order.verify(teamWriteLockService).lockTeam(7L);
        order.verify(projectMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void teamProjectRecoveryStopsWhenTeamWasDissolved() {
        when(projectMapper.selectDeletedById(9L)).thenReturn(deletedTeamProject());
        doThrow(new PermissionDeniedException()).when(teamWriteLockService).lockTeam(7L);

        assertThrows(PermissionDeniedException.class, () -> service.recover(9L));

        verify(projectMapper, never()).update(isNull(), any(Wrapper.class));
    }

    private Project deletedTeamProject() {
        Project project = new Project();
        project.setId(9L);
        project.setTeamId(7L);
        project.setDeletedAt(LocalDateTime.now().minusDays(1));
        project.setIsDelete(1);
        return project;
    }
}
