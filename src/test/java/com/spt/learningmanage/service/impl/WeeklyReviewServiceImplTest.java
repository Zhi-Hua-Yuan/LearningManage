package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.review.WeeklyReviewSaveRequest;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.vo.review.WeeklyReviewDetailVO;
import com.spt.learningmanage.service.PermissionService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private TaskMapper taskMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private PermissionService permissionService;
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
        when(weeklyReviewMapper.selectOne(any())).thenReturn(null);
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
