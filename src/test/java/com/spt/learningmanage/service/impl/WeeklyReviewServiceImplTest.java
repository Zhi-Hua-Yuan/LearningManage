package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.mapper.WeeklyReviewTaskMapper;
import com.spt.learningmanage.model.access.ProjectAccessScope;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.model.dto.review.WeeklyReviewSaveRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.entity.WeeklyReviewTask;
import com.spt.learningmanage.model.vo.review.WeeklyReviewSharedVO;
import com.spt.learningmanage.model.vo.review.WeeklyReviewVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReviewServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WeeklyReview.class);
        TableInfoHelper.initTableInfo(assistant, WeeklyReviewTask.class);
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, Project.class);
    }

    @Mock private WeeklyReviewMapper weeklyReviewMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private WeeklyReviewTaskMapper weeklyReviewTaskMapper;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private WeeklyReviewServiceImpl service;

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void saveTeamReview_shouldRequireNonBlankSharedSummary() {
        UserHolder.set(11L);
        WeeklyReviewSaveRequest request = request("TEAM");
        request.setSharedSummary("  ");

        assertThrows(BusinessException.class, () -> service.saveReview(request));

        verify(weeklyReviewMapper, never()).insert(any(WeeklyReview.class));
        verify(permissionService, never()).requireActiveTeamMember(any(), any());
    }

    @Test
    void saveTeamReview_shouldRejectFocusProjectFromAnotherTeam() {
        UserHolder.set(11L);
        WeeklyReviewSaveRequest request = request("TEAM");
        request.setTeamId(31L);
        request.setFocusProjectId(201L);
        request.setSharedSummary("本周进展");
        when(permissionService.requireProjectView(11L, 201L))
                .thenReturn(new ProjectAccessScope(201L, 41L, 22L, TeamRoleEnum.MEMBER, false, true, false));

        assertThrows(BusinessException.class, () -> service.saveReview(request));

        verify(permissionService).requireActiveTeamMember(11L, 31L);
        verify(weeklyReviewMapper, never()).insert(any(WeeklyReview.class));
    }

    @Test
    void savePrivateReview_shouldClearTeamIdWhenSwitchingFromTeam() {
        UserHolder.set(11L);
        WeeklyReview existing = review(501L, 11L, "TEAM", 31L);
        when(weeklyReviewMapper.selectById(501L)).thenReturn(existing);
        when(weeklyReviewMapper.updateById(any(WeeklyReview.class))).thenReturn(1);

        WeeklyReviewSaveRequest request = request("PRIVATE");
        request.setId(501L);
        request.setTeamId(null);
        request.setSharedSummary(null);

        service.updateReview(request);

        assertEquals("PRIVATE", existing.getVisibilityScope());
        assertEquals(null, existing.getTeamId());
        verify(weeklyReviewMapper).updateById(existing);
    }

    @Test
    void listTeamSharedReviews_shouldReturnSafeSharedViewOnly() {
        UserHolder.set(12L);
        WeeklyReview review = review(501L, 11L, "TEAM", 31L);
        review.setSharedSummary("可共享摘要");
        review.setReflection("私人反思");
        review.setNextPlan("私人计划");
        when(weeklyReviewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(review));

        List<WeeklyReviewSharedVO> result = service.listTeamSharedReviews(31L, null, null);

        assertEquals(1, result.size());
        assertEquals("可共享摘要", result.get(0).getSharedSummary());
        verify(permissionService).requireActiveTeamMember(12L, 31L);
        assertFalse(hasField(WeeklyReviewSharedVO.class, "reflection"));
        assertFalse(hasField(WeeklyReviewSharedVO.class, "nextPlan"));
        assertFalse(hasField(WeeklyReviewSharedVO.class, "taskIds"));
    }

    @Test
    void fullView_shouldIncludePrivateContentAndTaskLinks() {
        UserHolder.set(11L);
        WeeklyReview review = review(501L, 11L, "PRIVATE", null);
        review.setReflection("私人反思");
        review.setNextPlan("下周计划");
        WeeklyReviewTask link = new WeeklyReviewTask();
        link.setTaskId(801L);
        when(weeklyReviewMapper.selectById(501L)).thenReturn(review);
        when(weeklyReviewTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(link));

        WeeklyReviewVO result = service.getReviewViewById(501L);

        assertEquals("私人反思", result.getReflection());
        assertEquals("下周计划", result.getNextPlan());
        assertEquals(List.of(801L), result.getTaskIds());
        verify(permissionService).requireWeeklyReviewFullView(11L, 501L);
    }

    private WeeklyReviewSaveRequest request(String visibilityScope) {
        WeeklyReviewSaveRequest request = new WeeklyReviewSaveRequest();
        request.setYear(2026);
        request.setWeekNo(35);
        request.setStartDate(LocalDate.of(2026, 8, 24));
        request.setEndDate(LocalDate.of(2026, 8, 30));
        request.setCompletedTaskCount(0);
        request.setVisibilityScope(visibilityScope);
        return request;
    }

    private WeeklyReview review(Long id, Long userId, String scope, Long teamId) {
        WeeklyReview review = new WeeklyReview();
        review.setId(id);
        review.setUserId(userId);
        review.setYear(2026);
        review.setWeekNo(35);
        review.setStartDate(LocalDate.of(2026, 8, 24));
        review.setEndDate(LocalDate.of(2026, 8, 30));
        review.setVisibilityScope(scope);
        review.setTeamId(teamId);
        review.setCompletedTaskCount(1);
        return review;
    }

    private boolean hasField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            return field != null;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }
}
