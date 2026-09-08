package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.project.ProjectCreateRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectOrderingServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Project.class);
    }

    @Mock private ProjectMapper projectMapper;
    @Mock private UserMapper userMapper;
    @Mock private PermissionService permissionService;
    @InjectMocks private ProjectServiceImpl service;

    @AfterEach
    void cleanup() {
        UserHolder.remove();
    }

    @Test
    void personalCreateLocksActorBeforeAllocatingOrderNumber() {
        UserHolder.set(7L);
        User actor = new User();
        actor.setId(7L);
        when(userMapper.selectActiveByIdForUpdate(7L)).thenReturn(actor);
        when(projectMapper.selectMaxPersonalOrderNoForUpdate(7L)).thenReturn(null);
        when(projectMapper.insert(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(11L);
            return 1;
        });
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setName("ordered");

        assertEquals(11L, service.create(request));

        InOrder order = inOrder(userMapper, projectMapper);
        order.verify(userMapper).selectActiveByIdForUpdate(7L);
        order.verify(projectMapper).selectMaxPersonalOrderNoForUpdate(7L);
        order.verify(projectMapper).insert(any(Project.class));
    }
}
