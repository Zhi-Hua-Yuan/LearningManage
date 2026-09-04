package com.spt.learningmanage.service.ai.scene;

import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;

public interface TodayOrderAiService {
    AiTodayOrderVO recommendTodayOrder(AiTodayOrderRequest request);
}
