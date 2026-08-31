package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.mapper.WeeklyReviewTaskMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.model.dto.review.WeeklyReviewSaveRequest;
import com.spt.learningmanage.model.dto.review.WeeklyReviewUpdateRequest;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.WeeklyReviewTask;
import com.spt.learningmanage.model.vo.review.WeeklyReviewDetailVO;
import com.spt.learningmanage.model.review.WeeklyReviewAssociationContext;
import com.spt.learningmanage.model.review.WeeklyReviewReadableAssociations;
import com.spt.learningmanage.model.query.review.WeeklyReviewFocusProjectRow;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.WeeklyReviewAssociationValidator;
import com.spt.learningmanage.service.WeeklyReviewReadAssociationResolver;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class WeeklyReviewServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WeeklyReview.class);
    }

    @Mock
    private WeeklyReviewMapper weeklyReviewMapper;
    @Mock
    private WeeklyReviewTaskMapper weeklyReviewTaskMapper;
    @Mock
    private TeamMemberMapper teamMemberMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private WeeklyReviewAssociationValidator associationValidator;
    @Mock
    private WeeklyReviewReadAssociationResolver readAssociationResolver;
    @InjectMocks
    private WeeklyReviewServiceImpl weeklyReviewService;

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void save_shouldRejectPrivateReviewWithTeamId() {
        UserHolder.set(1L);
        WeeklyReviewSaveRequest request = request(2026, 35, "PRIVATE");
        request.setTeamId(10L);

        assertThrows(BusinessException.class, () -> weeklyReviewService.saveReview(request));
    }

    @Test
    void save_shouldPersistTeamScopeAndOnlySharedSummary() {
        UserHolder.set(1L);
        WeeklyReviewSaveRequest request = request(2026, 35, "TEAM");
        request.setTeamId(10L);
        request.setSharedSummary("  本周完成核心交付  ");
        TeamMember member = new TeamMember();
        member.setId(100L);
        member.setTeamId(10L);
        member.setUserId(1L);
        member.setRole("MEMBER");
        member.setIsDelete(0);
        when(teamMemberMapper.selectActiveMembersForUpdate(10L, List.of(1L)))
                .thenReturn(List.of(member));
        when(associationValidator.validate(any(), any(), any(), any(), any()))
                .thenReturn(WeeklyReviewAssociationContext.empty());
        when(weeklyReviewMapper.insert(any(WeeklyReview.class))).thenReturn(1);

        weeklyReviewService.saveReview(request);

        ArgumentCaptor<WeeklyReview> captor = ArgumentCaptor.forClass(WeeklyReview.class);
        verify(weeklyReviewMapper).insert(captor.capture());
        WeeklyReview saved = captor.getValue();
        assertEquals("TEAM", saved.getVisibilityScope());
        assertEquals(10L, saved.getTeamId());
        assertEquals("本周完成核心交付", saved.getSharedSummary());
        assertEquals(2026, saved.getYear());
        assertEquals(35, saved.getWeekNo());
        assertFalse(saved.getStartDate().isAfter(saved.getEndDate()));
        verify(permissionService).requireTeamView(1L, 10L);
    }

    @Test
    void getById_shouldReturnPrivateFieldsOnlyToFullViewPath() {
        UserHolder.set(1L);
        WeeklyReview review = new WeeklyReview();
        review.setId(7L);
        review.setUserId(1L);
        review.setYear(2026);
        review.setWeekNo(35);
        review.setVisibilityScope("PRIVATE");
        review.setReflection("private");
        review.setNextPlan("next");
        when(weeklyReviewMapper.selectById(7L)).thenReturn(review);

        WeeklyReviewDetailVO result = weeklyReviewService.getReviewById(7L);

        assertEquals("private", result.getReflection());
        assertEquals("next", result.getNextPlan());
        assertEquals("PRIVATE", result.getVisibilityScope());
        verify(permissionService).requireWeeklyReviewFullView(1L, 7L);
    }

    @Test
    void getById_shouldUseCurrentReadAssociationProjection() {
        UserHolder.set(1L);
        WeeklyReview review = new WeeklyReview();
        review.setId(7L);
        review.setUserId(1L);
        review.setFocusProjectId(55L);
        review.setFocusProjectName("已失权项目");
        when(weeklyReviewMapper.selectById(7L)).thenReturn(review);
        when(readAssociationResolver.resolve(1L, List.of(review)))
                .thenReturn(new WeeklyReviewReadableAssociations(
                        java.util.Map.of(7L, List.of(102L)), java.util.Set.of()));

        WeeklyReviewDetailVO result = weeklyReviewService.getReviewById(7L);

        assertEquals(List.of(102L), result.getTaskIds());
        assertEquals(null, result.getFocusProjectId());
        assertEquals(null, result.getFocusProjectName());
    }

    @Test
    void current_shouldUseAssigneeStatisticsAndKeepFocusIdNameConsistent() {
        UserHolder.set(1L);
        WeeklyReviewFocusProjectRow focus = new WeeklyReviewFocusProjectRow();
        focus.setProjectId(55L);
        focus.setProjectName("团队重点项目");
        focus.setCompletedCount(3);
        when(taskMapper.countWeeklyCompletedTasksByAssignee(any(), any(), any())).thenReturn(3L);
        when(taskMapper.selectWeeklyFocusProjectByAssignee(any(), any(), any())).thenReturn(focus);
        when(permissionService.resolveProjectScopes(eq(1L), any()))
                .thenReturn(java.util.Map.of(55L,
                        new ProjectAccessScope(1L, 55L, 9L, 10L, TeamRoleEnum.MEMBER)));

        WeeklyReviewDetailVO result = weeklyReviewService.getCurrentWeekReview();

        assertEquals(3, result.getCompletedTaskCount());
        assertEquals(55L, result.getFocusProjectId());
        assertEquals("团队重点项目", result.getFocusProjectName());
        verify(taskMapper).countWeeklyCompletedTasksByAssignee(eq(1L), any(), any());
        verify(taskMapper).selectWeeklyFocusProjectByAssignee(eq(1L), any(), any());
    }

    @Test
    void history_shouldResolveAllReviewAssociationsAsOneProjection() {
        UserHolder.set(1L);
        WeeklyReview first = new WeeklyReview();
        first.setId(7001L);
        first.setUserId(1L);
        first.setVisibilityScope("PRIVATE");
        WeeklyReview second = new WeeklyReview();
        second.setId(7002L);
        second.setUserId(1L);
        second.setVisibilityScope("PRIVATE");
        when(weeklyReviewMapper.selectList(any())).thenReturn(List.of(first, second));
        WeeklyReviewReadableAssociations associations = new WeeklyReviewReadableAssociations(
                java.util.Map.of(7001L, List.of(101L), 7002L, List.of()), java.util.Set.of());
        when(readAssociationResolver.resolve(1L, List.of(first, second))).thenReturn(associations);

        List<WeeklyReviewDetailVO> result = weeklyReviewService.listHistory();

        assertEquals(List.of(101L), result.get(0).getTaskIds());
        assertEquals(List.of(), result.get(1).getTaskIds());
        verify(readAssociationResolver, times(1)).resolve(1L, List.of(first, second));
    }

    @Test
    void save_shouldPersistFocusProjectAndTaskAssociationsAsOneBatch() {
        UserHolder.set(1L);
        WeeklyReviewSaveRequest request = request(2026, 35, "PRIVATE");
        request.setFocusProjectId(55L);
        request.setTaskIds(List.of(101L, 102L));
        when(associationValidator.validate(any(), any(), any(), any(), any()))
                .thenReturn(new WeeklyReviewAssociationContext(55L, "重点项目", List.of(101L, 102L)));
        when(weeklyReviewMapper.selectByUserYearWeekForUpdate(1L, 2026, 35)).thenReturn(null);
        when(weeklyReviewMapper.insert(any(WeeklyReview.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, WeeklyReview.class).setId(7001L);
            return 1;
        });
        when(weeklyReviewTaskMapper.batchInsert(any())).thenReturn(2);

        weeklyReviewService.saveReview(request);

        ArgumentCaptor<WeeklyReview> reviewCaptor = ArgumentCaptor.forClass(WeeklyReview.class);
        verify(weeklyReviewMapper).insert(reviewCaptor.capture());
        assertEquals(55L, reviewCaptor.getValue().getFocusProjectId());
        assertEquals("重点项目", reviewCaptor.getValue().getFocusProjectName());
        ArgumentCaptor<List<WeeklyReviewTask>> relationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(weeklyReviewTaskMapper).batchInsert(relationsCaptor.capture());
        assertEquals(List.of(101L, 102L), relationsCaptor.getValue().stream()
                .map(WeeklyReviewTask::getTaskId).toList());
        assertEquals(List.of(7001L, 7001L), relationsCaptor.getValue().stream()
                .map(WeeklyReviewTask::getWeeklyReviewId).toList());
    }

    @Test
    void update_shouldReplaceAssociationCollectionAndClearWhenEmpty() {
        UserHolder.set(1L);
        WeeklyReview existing = new WeeklyReview();
        existing.setId(7002L);
        existing.setUserId(1L);
        existing.setYear(2026);
        existing.setWeekNo(35);
        existing.setVisibilityScope("PRIVATE");
        WeeklyReviewUpdateRequest request = new WeeklyReviewUpdateRequest();
        request.setId(7002L);
        request.setVisibilityScope("PRIVATE");
        request.setTaskIds(List.of());
        when(weeklyReviewMapper.selectByIdForUpdate(7002L)).thenReturn(existing);
        when(associationValidator.validate(any(), any(), any(), any(), any()))
                .thenReturn(WeeklyReviewAssociationContext.empty());
        when(weeklyReviewMapper.updateForWrite(existing)).thenReturn(1);
        when(weeklyReviewTaskMapper.deleteByReviewId(7002L)).thenReturn(2);

        weeklyReviewService.updateReview(request);

        verify(weeklyReviewTaskMapper).deleteByReviewId(7002L);
        verify(weeklyReviewTaskMapper, times(0)).batchInsert(any());
        assertEquals(null, existing.getFocusProjectId());
    }

    @Test
    void delete_shouldRemoveRelationsBeforeReviewRow() {
        UserHolder.set(1L);
        WeeklyReview review = new WeeklyReview();
        review.setId(7003L);
        review.setUserId(1L);
        when(weeklyReviewMapper.selectByIdForUpdate(7003L)).thenReturn(review);
        when(weeklyReviewTaskMapper.deleteByReviewId(7003L)).thenReturn(3);
        when(weeklyReviewMapper.deleteById(7003L)).thenReturn(1);

        weeklyReviewService.deleteReview(7003L);

        var inOrder = org.mockito.Mockito.inOrder(weeklyReviewTaskMapper, weeklyReviewMapper);
        inOrder.verify(weeklyReviewTaskMapper).deleteByReviewId(7003L);
        inOrder.verify(weeklyReviewMapper).deleteById(7003L);
    }

    @Test
    void save_shouldFailWhenAssociationBatchIsNotFullyInserted() {
        UserHolder.set(1L);
        WeeklyReviewSaveRequest request = request(2026, 36, "PRIVATE");
        request.setTaskIds(List.of(201L));
        when(associationValidator.validate(any(), any(), any(), any(), any()))
                .thenReturn(new WeeklyReviewAssociationContext(null, null, List.of(201L)));
        when(weeklyReviewMapper.selectByUserYearWeekForUpdate(1L, 2026, 36)).thenReturn(null);
        when(weeklyReviewMapper.insert(any(WeeklyReview.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, WeeklyReview.class).setId(7004L);
            return 1;
        });
        when(weeklyReviewTaskMapper.batchInsert(any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> weeklyReviewService.saveReview(request));
        verify(weeklyReviewTaskMapper).deleteByReviewId(7004L);
        verify(weeklyReviewTaskMapper).batchInsert(any());
    }

    private WeeklyReviewSaveRequest request(int year, int weekNo, String scope) {
        WeeklyReviewSaveRequest request = new WeeklyReviewSaveRequest();
        request.setYear(year);
        request.setWeekNo(weekNo);
        request.setVisibilityScope(scope);
        request.setReflection("private text");
        request.setNextPlan("next step");
        return request;
    }
}
