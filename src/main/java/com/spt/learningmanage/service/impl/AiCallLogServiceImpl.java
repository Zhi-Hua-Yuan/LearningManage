package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiCallLogMapper;
import com.spt.learningmanage.model.entity.AiCallLog;
import com.spt.learningmanage.service.AiCallLogService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AiCallLogServiceImpl implements AiCallLogService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;
    private static final String UNKNOWN_VALUE = "unknown";

    @Resource
    private AiCallLogMapper aiCallLogMapper;

    @Override
    public Long createRunningLog(Long userId,
                                 String scene,
                                 String modelName,
                                 String promptType,
                                 String requestText,
                                 Integer retryCount) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }

        AiCallLog callLog = new AiCallLog();
        callLog.setUserId(userId);
        callLog.setScene(defaultIfBlank(scene));
        callLog.setModelName(defaultIfBlank(modelName));
        callLog.setPromptType(promptType);
        callLog.setRequestText(requestText);
        callLog.setStatus(AiCallLogStatusEnum.RUNNING.getValue());
        callLog.setRetryCount(retryCount == null || retryCount < 0 ? 0 : retryCount);

        int rows = aiCallLogMapper.insert(callLog);
        if (rows != 1 || callLog.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用记录创建失败");
        }
        return callLog.getId();
    }

    @Override
    public void markSuccess(Long logId, String responseText, Long costTimeMs) {
        updateLog(logId, AiCallLogStatusEnum.SUCCESS.getValue(), responseText, null, costTimeMs);
    }

    @Override
    public void markFailed(Long logId, String errorMessage, Long costTimeMs) {
        updateLog(logId, AiCallLogStatusEnum.FAILED.getValue(), null, errorMessage, costTimeMs);
    }

    @Override
    public void markParseFailed(Long logId, String responseText, String errorMessage, Long costTimeMs) {
        updateLog(logId, AiCallLogStatusEnum.PARSE_FAILED.getValue(), responseText, errorMessage, costTimeMs);
    }

    @Override
    public void markTimeout(Long logId, String errorMessage, Long costTimeMs) {
        updateLog(logId, AiCallLogStatusEnum.TIMEOUT.getValue(), null, errorMessage, costTimeMs);
    }

    private void updateLog(Long logId, Integer status, String responseText, String errorMessage, Long costTimeMs) {
        if (logId == null || logId <= 0) {
            return;
        }

        aiCallLogMapper.update(null, new LambdaUpdateWrapper<AiCallLog>()
                .eq(AiCallLog::getId, logId)
                .set(AiCallLog::getStatus, status)
                .set(AiCallLog::getResponseText, responseText)
                .set(AiCallLog::getErrorMessage, truncate(errorMessage, ERROR_MESSAGE_MAX_LENGTH))
                .set(AiCallLog::getCostTimeMs, costTimeMs));
    }

    private String defaultIfBlank(String value) {
        return StrUtil.isBlank(value) ? UNKNOWN_VALUE : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
