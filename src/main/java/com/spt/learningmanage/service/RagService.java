package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;
import com.spt.learningmanage.service.rag.RagExecutionObserver;

public interface RagService {
    RagAnswerVO ask(RagAskRequest request);

    RagAnswerVO ask(RagAskRequest request, Long actorUserId, RagExecutionObserver observer);

    RagAnswerVO ask(RagAskRequest request, Long actorUserId,
                    RagExecutionObserver observer, String requestId);

    RagAnswerVO getResult(String requestId);
}
