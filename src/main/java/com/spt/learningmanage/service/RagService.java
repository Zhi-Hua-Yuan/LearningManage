package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;

public interface RagService {
    RagAnswerVO ask(RagAskRequest request);

    RagAnswerVO getResult(String requestId);
}
