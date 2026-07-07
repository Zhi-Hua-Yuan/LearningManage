package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiCallLogMapper;
import com.spt.learningmanage.model.dto.ai.AiCallLogQueryRequest;
import com.spt.learningmanage.model.entity.AiCallLog;
import com.spt.learningmanage.model.vo.ai.AiCallLogDetailVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogVO;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AiCallLogServiceImpl implements AiCallLogService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;
    private static final int LIST_TEXT_PREVIEW_MAX_LENGTH = 300;
    private static final int DETAIL_TEXT_MAX_LENGTH = 10000;
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

    @Override
    public Page<AiCallLogVO> list(AiCallLogQueryRequest request) {
        Long userId = getCurrentUserId();
        AiCallLogQueryRequest validRequest = request == null ? new AiCallLogQueryRequest() : request;
        validateQueryRequest(validRequest);

        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCallLog::getUserId, userId);
        if (StrUtil.isNotBlank(validRequest.getScene())) {
            wrapper.eq(AiCallLog::getScene, validRequest.getScene().trim());
        }
        if (validRequest.getStatus() != null) {
            wrapper.eq(AiCallLog::getStatus, validRequest.getStatus());
        }
        if (StrUtil.isNotBlank(validRequest.getModelName())) {
            wrapper.eq(AiCallLog::getModelName, validRequest.getModelName().trim());
        }
        if (StrUtil.isNotBlank(validRequest.getPromptType())) {
            wrapper.eq(AiCallLog::getPromptType, validRequest.getPromptType().trim());
        }
        if (validRequest.getStartTime() != null) {
            wrapper.ge(AiCallLog::getCreateTime, validRequest.getStartTime());
        }
        if (validRequest.getEndTime() != null) {
            wrapper.le(AiCallLog::getCreateTime, validRequest.getEndTime());
        }
        wrapper.orderByDesc(AiCallLog::getCreateTime);

        Page<AiCallLog> page = new Page<>(safePageNum(validRequest.getPageNum()), safePageSize(validRequest.getPageSize()));
        Page<AiCallLog> resultPage = aiCallLogMapper.selectPage(page, wrapper);
        Page<AiCallLogVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::toListVO).toList());
        return voPage;
    }

    @Override
    public AiCallLogDetailVO getDetail(Long id) {
        Long userId = getCurrentUserId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "调用记录 ID 不合法");
        }

        AiCallLog callLog = aiCallLogMapper.selectOne(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getId, id)
                .eq(AiCallLog::getUserId, userId)
                .last("limit 1"));
        if (callLog == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "调用记录不存在或无权限");
        }
        return toDetailVO(callLog);
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

    private Long getCurrentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }

    private void validateQueryRequest(AiCallLogQueryRequest request) {
        if (request.getStatus() != null && AiCallLogStatusEnum.fromValue(request.getStatus()) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "调用状态不合法");
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getStartTime().isAfter(request.getEndTime())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "开始时间不能晚于结束时间");
        }
    }

    private AiCallLogVO toListVO(AiCallLog callLog) {
        AiCallLogVO vo = new AiCallLogVO();
        vo.setId(callLog.getId());
        vo.setScene(callLog.getScene());
        vo.setModelName(callLog.getModelName());
        vo.setPromptType(callLog.getPromptType());
        vo.setRequestPreview(truncate(callLog.getRequestText(), LIST_TEXT_PREVIEW_MAX_LENGTH));
        vo.setResponsePreview(truncate(callLog.getResponseText(), LIST_TEXT_PREVIEW_MAX_LENGTH));
        vo.setStatus(callLog.getStatus());
        vo.setStatusText(AiCallLogStatusEnum.getText(callLog.getStatus()));
        vo.setErrorMessage(callLog.getErrorMessage());
        vo.setCostTimeMs(callLog.getCostTimeMs());
        vo.setRetryCount(callLog.getRetryCount());
        vo.setCreateTime(callLog.getCreateTime());
        vo.setUpdateTime(callLog.getUpdateTime());
        return vo;
    }

    private AiCallLogDetailVO toDetailVO(AiCallLog callLog) {
        AiCallLogDetailVO vo = new AiCallLogDetailVO();
        vo.setId(callLog.getId());
        vo.setUserId(callLog.getUserId());
        vo.setScene(callLog.getScene());
        vo.setModelName(callLog.getModelName());
        vo.setPromptType(callLog.getPromptType());
        vo.setRequestText(truncate(callLog.getRequestText(), DETAIL_TEXT_MAX_LENGTH));
        vo.setRequestTextTruncated(isTruncated(callLog.getRequestText(), DETAIL_TEXT_MAX_LENGTH));
        vo.setResponseText(truncate(callLog.getResponseText(), DETAIL_TEXT_MAX_LENGTH));
        vo.setResponseTextTruncated(isTruncated(callLog.getResponseText(), DETAIL_TEXT_MAX_LENGTH));
        vo.setStatus(callLog.getStatus());
        vo.setStatusText(AiCallLogStatusEnum.getText(callLog.getStatus()));
        vo.setErrorMessage(callLog.getErrorMessage());
        vo.setCostTimeMs(callLog.getCostTimeMs());
        vo.setRetryCount(callLog.getRetryCount());
        vo.setCreateTime(callLog.getCreateTime());
        vo.setUpdateTime(callLog.getUpdateTime());
        return vo;
    }

    private long safePageNum(Long pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1L;
        }
        return pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100L);
    }

    private boolean isTruncated(String value, int maxLength) {
        return value != null && value.length() > maxLength;
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
