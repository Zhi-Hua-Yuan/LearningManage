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
import com.spt.learningmanage.service.AiService;
import com.spt.learningmanage.service.ai.scene.AiChatCompatibilityService;
import com.spt.learningmanage.service.ai.scene.DailyRenameAiService;
import com.spt.learningmanage.service.ai.scene.ListReplanAiService;
import com.spt.learningmanage.service.ai.scene.TaskBreakdownAiService;
import com.spt.learningmanage.service.ai.scene.TodayOrderAiService;
import com.spt.learningmanage.service.ai.scene.WeeklyReviewAiService;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 兼容既有 AiService 契约的薄门面。场景行为、事务和持久化均由独立服务负责。
 */
@Service
public class AiServiceImpl implements AiService {

    private final AiChatCompatibilityService chatService;
    private final TaskBreakdownAiService taskBreakdownService;
    private final WeeklyReviewAiService weeklyReviewService;
    private final TodayOrderAiService todayOrderService;
    private final DailyRenameAiService dailyRenameService;
    private final ListReplanAiService listReplanService;
    private final AiDraftLifecycleService draftLifecycleService;

    public AiServiceImpl(AiChatCompatibilityService chatService,
                         TaskBreakdownAiService taskBreakdownService,
                         WeeklyReviewAiService weeklyReviewService,
                         TodayOrderAiService todayOrderService,
                         DailyRenameAiService dailyRenameService,
                         ListReplanAiService listReplanService,
                         AiDraftLifecycleService draftLifecycleService) {
        this.chatService = chatService;
        this.taskBreakdownService = taskBreakdownService;
        this.weeklyReviewService = weeklyReviewService;
        this.todayOrderService = todayOrderService;
        this.dailyRenameService = dailyRenameService;
        this.listReplanService = listReplanService;
        this.draftLifecycleService = draftLifecycleService;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chatService.chat(systemPrompt, userPrompt);
    }

    @Override
    public List<MilestoneDraftVO> generateTaskBreakdown(String target, String description,
                                                        String duration, boolean detailed) {
        return taskBreakdownService.generateTaskBreakdown(target, description, duration, detailed);
    }

    @Override
    public String polishWeeklyReview(List<Long> taskIds, String reflection) {
        return weeklyReviewService.polishWeeklyReview(taskIds, reflection);
    }

    @Override
    public AiTodayOrderVO recommendTodayOrder(AiTodayOrderRequest request) {
        return todayOrderService.recommendTodayOrder(request);
    }

    @Override
    public DailyReviewSuggestRenameVO suggestDailyReviewRename(DailyReviewSuggestRenameRequest request) {
        return dailyRenameService.suggestDailyReviewRename(request);
    }

    public boolean replanListTasks(Long listId) {
        return listReplanService.replanListTasks(listId);
    }

    @Override
    public AiListReplanPreviewVO previewListReplan(Long listId) {
        return listReplanService.previewListReplan(listId);
    }

    @Override
    public boolean confirmListReplan(Long listId, String operationId) {
        return listReplanService.confirmListReplan(listId, operationId);
    }

    @Override
    public boolean cancelListReplan(String operationId) {
        return listReplanService.cancelListReplan(operationId);
    }

    @Override
    public AiBreakdownPreviewVO previewTaskBreakdown(AiBreakdownRequest request) {
        return taskBreakdownService.previewTaskBreakdown(request);
    }

    @Override
    public AiDraftConfirmVO confirmTaskBreakdown(String draftId, String operationId,
                                                 String projectName, String projectGoal) {
        return taskBreakdownService.confirmTaskBreakdown(draftId, operationId, projectName, projectGoal);
    }

    @Override
    public boolean cancelDraft(String draftId, String scene) {
        return draftLifecycleService.cancelDraft(draftId, scene);
    }

    @Override
    public AiPolishPreviewVO previewWeeklyPolish(AiPolishRequest request) {
        return weeklyReviewService.previewWeeklyPolish(request);
    }

    @Override
    public AiDraftConfirmVO confirmWeeklyPolish(String draftId, String operationId, Long reviewId) {
        return weeklyReviewService.confirmWeeklyPolish(draftId, operationId, reviewId);
    }

    @Override
    public AiDraftDetailVO getDraftDetail(String draftId) {
        return draftLifecycleService.getDraftDetail(draftId);
    }

    @Override
    public int expirePreviewDrafts() {
        return draftLifecycleService.expirePreviewDrafts();
    }
}
