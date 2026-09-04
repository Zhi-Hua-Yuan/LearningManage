package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogQueryRequest;
import com.spt.learningmanage.model.dto.ai.AiCallLogStatsRequest;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.vo.ai.AiCallLogDetailVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogStatsVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogVO;

public interface AiCallLogService {

    Long createRunningLog(AiCallLogCreateCommand command);

    /**
     * 仅当日志仍处于 RUNNING 时写入唯一终态。
     *
     * @return 是否成功完成状态迁移
     */
    boolean complete(AiCallLogCompletionCommand command);

    void updateExecutionMetadata(Long logId, String actualModel, Integer retryCount);

    /**
     * 写入一次 Chat 调用的协议元数据。默认实现保持旧实现的二参数兼容。
     */
    default void updateProtocolMetadata(Long logId, AiChatResult result, String traceId) {
        if (result != null) {
            updateExecutionMetadata(logId, result.actualModel(), result.retryCount());
        }
    }

    void markSuccess(Long logId, String responseText, Long costTimeMs);

    void markFailed(Long logId, String errorMessage, Long costTimeMs);

    void markParseFailed(Long logId, String responseText, String errorMessage, Long costTimeMs);

    void markTimeout(Long logId, String errorMessage, Long costTimeMs);

    Page<AiCallLogVO> list(AiCallLogQueryRequest request);

    AiCallLogDetailVO getDetail(Long id);

    AiCallLogStatsVO getStats(AiCallLogStatsRequest request);
}
