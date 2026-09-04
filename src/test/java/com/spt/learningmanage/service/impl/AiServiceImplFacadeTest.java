package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.vo.ai.AiBreakdownPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.ai.AiDraftDetailVO;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiPolishPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.service.ai.scene.AiChatCompatibilityService;
import com.spt.learningmanage.service.ai.scene.DailyRenameAiService;
import com.spt.learningmanage.service.ai.scene.ListReplanAiService;
import com.spt.learningmanage.service.ai.scene.TaskBreakdownAiService;
import com.spt.learningmanage.service.ai.scene.TodayOrderAiService;
import com.spt.learningmanage.service.ai.scene.WeeklyReviewAiService;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiServiceImplFacadeTest {

    @Test
    void everyCompatibilityMethodDelegatesToItsOwner() {
        AiChatCompatibilityService chat = mock(AiChatCompatibilityService.class);
        TaskBreakdownAiService breakdown = mock(TaskBreakdownAiService.class);
        WeeklyReviewAiService weekly = mock(WeeklyReviewAiService.class);
        TodayOrderAiService today = mock(TodayOrderAiService.class);
        DailyRenameAiService daily = mock(DailyRenameAiService.class);
        ListReplanAiService replan = mock(ListReplanAiService.class);
        AiDraftLifecycleService drafts = mock(AiDraftLifecycleService.class);
        AiServiceImpl facade = new AiServiceImpl(chat, breakdown, weekly, today, daily, replan, drafts);

        List<MilestoneDraftVO> breakdownResult = List.of(new MilestoneDraftVO());
        AiBreakdownRequest breakdownRequest = new AiBreakdownRequest();
        AiBreakdownPreviewVO breakdownPreview = new AiBreakdownPreviewVO();
        AiDraftConfirmVO confirmResult = new AiDraftConfirmVO();
        AiPolishRequest polishRequest = new AiPolishRequest();
        AiPolishPreviewVO polishPreview = new AiPolishPreviewVO();
        AiTodayOrderRequest todayRequest = new AiTodayOrderRequest();
        AiTodayOrderVO todayResult = new AiTodayOrderVO();
        DailyReviewSuggestRenameRequest dailyRequest = new DailyReviewSuggestRenameRequest();
        DailyReviewSuggestRenameVO dailyResult = new DailyReviewSuggestRenameVO();
        AiListReplanPreviewVO replanPreview = new AiListReplanPreviewVO();
        AiDraftDetailVO detail = new AiDraftDetailVO();

        when(chat.chat("system", "user")).thenReturn("answer");
        when(breakdown.generateTaskBreakdown("target", "description", "duration", true)).thenReturn(breakdownResult);
        when(breakdown.previewTaskBreakdown(breakdownRequest)).thenReturn(breakdownPreview);
        when(breakdown.confirmTaskBreakdown("draft", "operation", "project", "goal")).thenReturn(confirmResult);
        when(weekly.polishWeeklyReview(List.of(1L), "reflection")).thenReturn("polished");
        when(weekly.previewWeeklyPolish(polishRequest)).thenReturn(polishPreview);
        when(weekly.confirmWeeklyPolish("draft", "operation", 9L)).thenReturn(confirmResult);
        when(today.recommendTodayOrder(todayRequest)).thenReturn(todayResult);
        when(daily.suggestDailyReviewRename(dailyRequest)).thenReturn(dailyResult);
        when(replan.replanListTasks(7L)).thenReturn(true);
        when(replan.previewListReplan(7L)).thenReturn(replanPreview);
        when(replan.confirmListReplan(7L, "operation")).thenReturn(true);
        when(replan.cancelListReplan("operation")).thenReturn(true);
        when(drafts.cancelDraft("draft", "scene")).thenReturn(true);
        when(drafts.getDraftDetail("draft")).thenReturn(detail);
        when(drafts.expirePreviewDrafts()).thenReturn(3);

        org.junit.jupiter.api.Assertions.assertEquals("answer", facade.chat("system", "user"));
        assertSame(breakdownResult, facade.generateTaskBreakdown("target", "description", "duration", true));
        assertSame(breakdownPreview, facade.previewTaskBreakdown(breakdownRequest));
        assertSame(confirmResult, facade.confirmTaskBreakdown("draft", "operation", "project", "goal"));
        org.junit.jupiter.api.Assertions.assertEquals("polished", facade.polishWeeklyReview(List.of(1L), "reflection"));
        assertSame(polishPreview, facade.previewWeeklyPolish(polishRequest));
        assertSame(confirmResult, facade.confirmWeeklyPolish("draft", "operation", 9L));
        assertSame(todayResult, facade.recommendTodayOrder(todayRequest));
        assertSame(dailyResult, facade.suggestDailyReviewRename(dailyRequest));
        assertTrue(facade.replanListTasks(7L));
        assertSame(replanPreview, facade.previewListReplan(7L));
        assertTrue(facade.confirmListReplan(7L, "operation"));
        assertTrue(facade.cancelListReplan("operation"));
        assertTrue(facade.cancelDraft("draft", "scene"));
        assertSame(detail, facade.getDraftDetail("draft"));
        org.junit.jupiter.api.Assertions.assertEquals(3, facade.expirePreviewDrafts());

        verify(chat).chat("system", "user");
        verify(breakdown).previewTaskBreakdown(breakdownRequest);
        verify(weekly).previewWeeklyPolish(polishRequest);
        verify(today).recommendTodayOrder(todayRequest);
        verify(daily).suggestDailyReviewRename(dailyRequest);
        verify(replan).previewListReplan(7L);
        verify(drafts).getDraftDetail("draft");
    }
}
