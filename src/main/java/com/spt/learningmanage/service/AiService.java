package com.spt.learningmanage.service;

import com.spt.learningmanage.model.vo.MilestoneDraftVO;

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
    List<MilestoneDraftVO> generateTaskBreakdown(String target, String description, String duration);

    /**
     * 润色周总结内容。
     */
    String polishWeeklyReview(Integer taskCount, String focusProject, String reflection);
}

