package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewVO;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;

import java.util.List;

public interface AiService {

    /**
     * 调用大模型进行对话生成。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 大模型返回文本
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 生成任务拆解草稿（仅返回结构化建议，不落库）。
     */
    List<MilestoneDraftVO> generateTaskBreakdown(String target, String description, String duration, boolean detailed);

    /**
     * 润色周总结内容。
     */
    String polishWeeklyReview(List<Long> taskIds, String reflection);

    AiTodayOrderVO recommendTodayOrder(AiTodayOrderRequest request);

    DailyReviewSuggestRenameVO suggestDailyReviewRename(DailyReviewSuggestRenameRequest request);

    AiListReplanPreviewVO previewListReplan(Long listId);

    boolean confirmListReplan(Long listId, String operationId);

    boolean cancelListReplan(String operationId);
}
