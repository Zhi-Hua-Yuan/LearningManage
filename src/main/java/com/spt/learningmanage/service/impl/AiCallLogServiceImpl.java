package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiCallLogMapper;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogQueryRequest;
import com.spt.learningmanage.model.dto.ai.AiCallLogStatsRequest;
import com.spt.learningmanage.model.entity.AiCallLog;
import com.spt.learningmanage.model.vo.ai.AiCallLogDetailVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogSceneStatsVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogStatsVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogStatusStatsVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogVO;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
public class AiCallLogServiceImpl implements AiCallLogService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;
    private static final int LIST_TEXT_PREVIEW_MAX_LENGTH = 300;
    private static final int DETAIL_TEXT_MAX_LENGTH = 10000;
    private static final String UNKNOWN_VALUE = "unknown";

    @Resource
    private AiCallLogMapper aiCallLogMapper;

    @Override
    public Long createRunningLog(AiCallLogCreateCommand command) {
        if (command == null || command.userId() == null || command.userId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }

        AiCallLog callLog = new AiCallLog();
        callLog.setUserId(command.userId());
        callLog.setScene(defaultIfBlank(command.scene()));
        callLog.setModelName(defaultIfBlank(command.modelName()));
        callLog.setPromptType(command.promptCode());
        callLog.setPromptTemplateId(command.promptTemplateId());
        callLog.setPromptVersion(command.promptVersion());
        callLog.setPromptSource(command.promptSource());
        callLog.setRequestText(command.requestText());
        callLog.setStatus(AiCallLogStatusEnum.RUNNING.getValue());
        callLog.setRetryCount(command.retryCount() == null || command.retryCount() < 0 ? 0 : command.retryCount());

        int rows = aiCallLogMapper.insert(callLog);
        if (rows != 1 || callLog.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用记录创建失败");
        }
        return callLog.getId();
    }

    @Override
    public void updateExecutionMetadata(Long logId, String actualModel, Integer retryCount) {
        if (logId == null || logId <= 0) {
            return;
        }
        if (StrUtil.isBlank(actualModel) && retryCount == null) {
            return;
        }

        LambdaUpdateWrapper<AiCallLog> wrapper = new LambdaUpdateWrapper<AiCallLog>()
                .eq(AiCallLog::getId, logId);
        if (StrUtil.isNotBlank(actualModel)) {
            wrapper.set(AiCallLog::getModelName, actualModel.trim());
        }
        if (retryCount != null) {
            wrapper.set(AiCallLog::getRetryCount, Math.max(retryCount, 0));
        }
        aiCallLogMapper.update(null, wrapper);
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

    @Override
    public AiCallLogStatsVO getStats(AiCallLogStatsRequest request) {
        Long userId = getCurrentUserId();
        AiCallLogStatsRequest validRequest = request == null ? new AiCallLogStatsRequest() : request;
        validateStatsRequest(validRequest);

        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCallLog::getUserId, userId);
        if (StrUtil.isNotBlank(validRequest.getScene())) {
            wrapper.eq(AiCallLog::getScene, validRequest.getScene().trim());
        }
        if (validRequest.getStartTime() != null) {
            wrapper.ge(AiCallLog::getCreateTime, validRequest.getStartTime());
        }
        if (validRequest.getEndTime() != null) {
            wrapper.le(AiCallLog::getCreateTime, validRequest.getEndTime());
        }

        List<AiCallLog> callLogs = aiCallLogMapper.selectList(wrapper);
        return buildStatsVO(callLogs);
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

    private void validateStatsRequest(AiCallLogStatsRequest request) {
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getStartTime().isAfter(request.getEndTime())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "开始时间不能晚于结束时间");
        }
    }

    private AiCallLogStatsVO buildStatsVO(List<AiCallLog> callLogs) {
        AiCallLogStatsVO vo = new AiCallLogStatsVO();
        long totalCount = callLogs.size();
        long successCount = countByStatus(callLogs, AiCallLogStatusEnum.SUCCESS);

        vo.setTotalCount(totalCount);
        vo.setRunningCount(countByStatus(callLogs, AiCallLogStatusEnum.RUNNING));
        vo.setSuccessCount(successCount);
        vo.setFailedCount(countByStatus(callLogs, AiCallLogStatusEnum.FAILED));
        vo.setParseFailedCount(countByStatus(callLogs, AiCallLogStatusEnum.PARSE_FAILED));
        vo.setTimeoutCount(countByStatus(callLogs, AiCallLogStatusEnum.TIMEOUT));
        vo.setSuccessRate(calculateSuccessRate(successCount, totalCount));
        vo.setAvgCostTimeMs(calculateAvgCostTimeMs(callLogs));
        vo.setMaxCostTimeMs(calculateMaxCostTimeMs(callLogs));
        vo.setMinCostTimeMs(calculateMinCostTimeMs(callLogs));
        vo.setSceneStats(buildSceneStats(callLogs));
        vo.setStatusStats(buildStatusStats(callLogs));
        return vo;
    }

    private List<AiCallLogSceneStatsVO> buildSceneStats(List<AiCallLog> callLogs) {
        Map<String, List<AiCallLog>> sceneLogMap = callLogs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        callLog -> defaultIfBlank(callLog.getScene()),
                        TreeMap::new,
                        java.util.stream.Collectors.toList()));

        return sceneLogMap.entrySet().stream()
                .map(entry -> buildSceneStatsVO(entry.getKey(), entry.getValue()))
                .toList();
    }

    private AiCallLogSceneStatsVO buildSceneStatsVO(String scene, List<AiCallLog> callLogs) {
        AiCallLogSceneStatsVO vo = new AiCallLogSceneStatsVO();
        long totalCount = callLogs.size();
        long successCount = countByStatus(callLogs, AiCallLogStatusEnum.SUCCESS);

        vo.setScene(scene);
        vo.setTotalCount(totalCount);
        vo.setRunningCount(countByStatus(callLogs, AiCallLogStatusEnum.RUNNING));
        vo.setSuccessCount(successCount);
        vo.setFailedCount(countByStatus(callLogs, AiCallLogStatusEnum.FAILED));
        vo.setParseFailedCount(countByStatus(callLogs, AiCallLogStatusEnum.PARSE_FAILED));
        vo.setTimeoutCount(countByStatus(callLogs, AiCallLogStatusEnum.TIMEOUT));
        vo.setSuccessRate(calculateSuccessRate(successCount, totalCount));
        vo.setAvgCostTimeMs(calculateAvgCostTimeMs(callLogs));
        return vo;
    }

    private List<AiCallLogStatusStatsVO> buildStatusStats(List<AiCallLog> callLogs) {
        return List.of(AiCallLogStatusEnum.values()).stream()
                .map(status -> buildStatusStatsVO(status, callLogs))
                .sorted(Comparator.comparing(AiCallLogStatusStatsVO::getStatus))
                .toList();
    }

    private AiCallLogStatusStatsVO buildStatusStatsVO(AiCallLogStatusEnum status, List<AiCallLog> callLogs) {
        AiCallLogStatusStatsVO vo = new AiCallLogStatusStatsVO();
        vo.setStatus(status.getValue());
        vo.setStatusText(status.getText());
        vo.setCount(countByStatus(callLogs, status));
        return vo;
    }

    private long countByStatus(List<AiCallLog> callLogs, AiCallLogStatusEnum status) {
        return callLogs.stream()
                .filter(callLog -> Objects.equals(callLog.getStatus(), status.getValue()))
                .count();
    }

    private BigDecimal calculateSuccessRate(long successCount, long totalCount) {
        if (totalCount <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(successCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
    }

    private Long calculateAvgCostTimeMs(List<AiCallLog> callLogs) {
        List<Long> costTimeList = getCostTimeList(callLogs);
        if (costTimeList.isEmpty()) {
            return null;
        }
        long totalCostTimeMs = costTimeList.stream().mapToLong(Long::longValue).sum();
        return BigDecimal.valueOf(totalCostTimeMs)
                .divide(BigDecimal.valueOf(costTimeList.size()), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private Long calculateMaxCostTimeMs(List<AiCallLog> callLogs) {
        return getCostTimeList(callLogs).stream()
                .max(Long::compareTo)
                .orElse(null);
    }

    private Long calculateMinCostTimeMs(List<AiCallLog> callLogs) {
        return getCostTimeList(callLogs).stream()
                .min(Long::compareTo)
                .orElse(null);
    }

    private List<Long> getCostTimeList(List<AiCallLog> callLogs) {
        return callLogs.stream()
                .map(AiCallLog::getCostTimeMs)
                .filter(Objects::nonNull)
                .toList();
    }

    private AiCallLogVO toListVO(AiCallLog callLog) {
        AiCallLogVO vo = new AiCallLogVO();
        vo.setId(callLog.getId());
        vo.setScene(callLog.getScene());
        vo.setModelName(callLog.getModelName());
        vo.setPromptType(callLog.getPromptType());
        vo.setPromptTemplateId(callLog.getPromptTemplateId());
        vo.setPromptVersion(callLog.getPromptVersion());
        vo.setPromptSource(callLog.getPromptSource());
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
        vo.setPromptTemplateId(callLog.getPromptTemplateId());
        vo.setPromptVersion(callLog.getPromptVersion());
        vo.setPromptSource(callLog.getPromptSource());
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
