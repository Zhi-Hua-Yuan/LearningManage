package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.knowledge.KnowledgeTextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeDocumentFactoryImplTest {

    @Test
    void teamReviewCreatesPrivateAndStrictSharedDocuments() {
        Fixture fixture = new Fixture();
        WeeklyReview review = review();
        Project project = project(10L, 20L, 1L);
        when(fixture.weeklyReviewMapper.selectById(30L)).thenReturn(review);
        when(fixture.projectMapper.selectOne(any(Wrapper.class))).thenReturn(project);
        when(fixture.teamMapper.selectOne(any(Wrapper.class))).thenReturn(team(20L));
        when(fixture.userMapper.selectById(1L)).thenReturn(user(1L));
        when(fixture.permissionService.resolveProjectScopes(1L, List.of(10L)))
                .thenReturn(Map.of(10L, new ProjectAccessScope(1L, 10L, 1L, 20L, TeamRoleEnum.MEMBER)));

        var documents = fixture.factory().buildDesiredDocuments(
                new KnowledgeSourceRef(KnowledgeSourceTypeEnum.WEEKLY_REVIEW, 30L));

        assertEquals(2, documents.size());
        var shared = documents.stream().filter(value -> value.visibilityType().name().equals("TEAM"))
                .findFirst().orElseThrow();
        assertTrue(shared.canonicalText().contains("公开进展"));
        assertFalse(shared.canonicalText().contains("私人反思"));
        assertFalse(shared.canonicalText().contains("私人计划"));
        assertEquals("WEEKLY_REVIEW:30:TEAM:10", shared.documentKey());
    }

    @Test
    void personalTaskContainsNoUserNameAndUsesPrivateOwnerScope() {
        Fixture fixture = new Fixture();
        Task task = new Task();
        task.setId(40L);
        task.setProjectId(10L);
        task.setCreatedByUserId(1L);
        task.setTitle("实现索引");
        task.setDescription("完成可重复构建");
        task.setUpdateTime(LocalDateTime.of(2026, 9, 6, 9, 0));
        when(fixture.taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(fixture.projectMapper.selectOne(any(Wrapper.class))).thenReturn(project(10L, null, 1L));

        var documents = fixture.factory().buildDesiredDocuments(
                new KnowledgeSourceRef(KnowledgeSourceTypeEnum.TASK, 40L));

        assertEquals(1, documents.size());
        assertEquals("TASK:40:PRIVATE:10", documents.get(0).documentKey());
        assertEquals(1L, documents.get(0).ownerUserId());
        assertEquals(1L, documents.get(0).payload().get("userId"));
        assertEquals(1L, documents.get(0).payload().get("ownerUserId"));
        assertEquals("2026-09-06T09:00:00+08:00", documents.get(0).payload().get("updatedAt"));
        assertFalse(documents.get(0).canonicalText().contains("用户"));
    }

    @Test
    void reviewProducesNoDocumentAfterAuthorLosesProjectAccess() {
        Fixture fixture = new Fixture();
        WeeklyReview review = review();
        Project project = project(10L, 20L, 2L);
        when(fixture.weeklyReviewMapper.selectById(30L)).thenReturn(review);
        when(fixture.projectMapper.selectOne(any(Wrapper.class))).thenReturn(project);
        when(fixture.teamMapper.selectOne(any(Wrapper.class))).thenReturn(team(20L));
        when(fixture.userMapper.selectById(1L)).thenReturn(user(1L));
        when(fixture.permissionService.resolveProjectScopes(1L, List.of(10L))).thenReturn(Map.of());

        var documents = fixture.factory().buildDesiredDocuments(
                new KnowledgeSourceRef(KnowledgeSourceTypeEnum.WEEKLY_REVIEW, 30L));

        assertTrue(documents.isEmpty());
    }

    private WeeklyReview review() {
        WeeklyReview review = new WeeklyReview();
        review.setId(30L);
        review.setUserId(1L);
        review.setYear(2026);
        review.setWeekNo(36);
        review.setFocusProjectId(10L);
        review.setTeamId(20L);
        review.setVisibilityScope("TEAM");
        review.setSharedSummary("公开进展");
        review.setReflection("私人反思");
        review.setNextPlan("私人计划");
        return review;
    }

    private Project project(Long id, Long teamId, Long ownerId) {
        Project project = new Project();
        project.setId(id);
        project.setTeamId(teamId);
        project.setUserId(ownerId);
        return project;
    }

    private Team team(Long id) {
        Team team = new Team();
        team.setId(id);
        return team;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static final class Fixture {
        private final TaskMapper taskMapper = mock(TaskMapper.class);
        private final WeeklyReviewMapper weeklyReviewMapper = mock(WeeklyReviewMapper.class);
        private final ProjectMapper projectMapper = mock(ProjectMapper.class);
        private final TeamMapper teamMapper = mock(TeamMapper.class);
        private final UserMapper userMapper = mock(UserMapper.class);
        private final PermissionService permissionService = mock(PermissionService.class);

        private KnowledgeDocumentFactoryImpl factory() {
            return new KnowledgeDocumentFactoryImpl(taskMapper, weeklyReviewMapper, projectMapper,
                    teamMapper, userMapper, permissionService, new KnowledgeTextNormalizer());
        }
    }
}
