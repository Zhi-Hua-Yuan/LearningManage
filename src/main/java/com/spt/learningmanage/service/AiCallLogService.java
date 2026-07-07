package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.ai.AiCallLogQueryRequest;
import com.spt.learningmanage.model.vo.ai.AiCallLogDetailVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogVO;

public interface AiCallLogService {

    Long createRunningLog(Long userId,
                          String scene,
                          String modelName,
                          String promptType,
                          String requestText,
                          Integer retryCount);

    void markSuccess(Long logId, String responseText, Long costTimeMs);

    void markFailed(Long logId, String errorMessage, Long costTimeMs);

    void markParseFailed(Long logId, String responseText, String errorMessage, Long costTimeMs);

    void markTimeout(Long logId, String errorMessage, Long costTimeMs);

    Page<AiCallLogVO> list(AiCallLogQueryRequest request);

    AiCallLogDetailVO getDetail(Long id);
}
