package com.spt.learningmanage.service.ai.scene;

import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewVO;

public interface ListReplanAiService {
    boolean replanListTasks(Long listId);

    AiListReplanPreviewVO previewListReplan(Long listId);

    boolean confirmListReplan(Long listId, String operationId);

    boolean cancelListReplan(String operationId);
}
