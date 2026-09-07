package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.ai.governance.AiCostCalculator;
import com.spt.learningmanage.ai.governance.AiCostEstimate;
import com.spt.learningmanage.ai.governance.AiSanitizedContent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiCallLogMapper;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogQueryRequest;
import com.spt.learningmanage.model.dto.ai.AiCallLogStatsRequest;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.entity.AiCallLog;
import com.spt.learningmanage.model.vo.ai.AiCallLogDetailVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogSceneStatsVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogStatsVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogStatusStatsVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogVO;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiCallLogOperationsService;
import com.spt.learningmanage.model.ops.CleanupBatchResult;
import java.time.LocalDateTime;
import com.spt.learningmanage.observability.AiMetricsRecorder;
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
public class AiCallLogServiceImpl implements AiCallLogService, AiCallLogOperationsService {

    private static final int LIST_TEXT_PREVIEW_MAX_LENGTH = 300;
    private static final int DETAIL_TEXT_MAX_LENGTH = 10000;
    private static final String UNKNOWN_VALUE = "unknown";

    @Resource
    private AiCallLogMapper aiCallLogMapper;

    @Resource
    private AiContentSanitizer aiContentSanitizer;

    @Resource
    private AiCostCalculator aiCostCalculator;

    @Resource
    private AiMetricsRecorder aiMetricsRecorder;

    @Override
    public Long createRunningLog(AiCallLogCreateCommand command) {
        if (command == null || command.userId() == null || command.userId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }

        AiCallLog callLog = new AiCallLog();
        callLog.setUserId(command.userId());
        callLog.setScene(defaultIfBlank(command.scene()));
        callLog.setModelName(defaultIfBlank(command.modelName()));
        callLog.setRequestedModel(defaultIfBlank(command.modelName()));
        callLog.setPromptType(command.promptCode());
        callLog.setPromptTemplateId(command.promptTemplateId());
        callLog.setPromptVersion(command.promptVersion());
        callLog.setPromptSource(command.promptSource());
        AiSanitizedContent request = aiContentSanitizer.sanitizeForLog(command.requestText(), false);
        callLog.setRequestText(request.value());
        callLog.setRequestSanitizationStatus(request.status().name());
        callLog.setRequestTruncated(request.truncated() ? 1 : 0);
        callLog.setRequestHash(request.sha256());
        callLog.setResponseSanitizationStatus("CLEAN");
        callLog.setErrorSanitizationStatus("CLEAN");
        callLog.setResponseTruncated(0);
        callLog.setErrorTruncated(0);
        callLog.setTraceId(defaultIfBlankNullable(command.traceId()));
        callLog.setStatus(AiCallLogStatusEnum.RUNNING.getValue());
        callLog.setRetryCount(command.retryCount() == null || command.retryCount() < 0 ? 0 : command.retryCount());

        int rows = aiCallLogMapper.insert(callLog);
        if (rows != 1 || callLog.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用记录创建失败");
        }
        if (StrUtil.isNotBlank(command.agentRunId()) && command.agentRoundNo() != null) {
            if (aiCallLogMapper.linkAgentRound(callLog.getId(), command.agentRunId().trim(),
                    command.agentRoundNo()) != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Agent 调用记录关联失败");
            }
        }
        return callLog.getId();
    }

    @Override
    public boolean complete(AiCallLogCompletionCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI 调用日志终态命令不能为空");
        }

        AiSanitizedContent response = aiContentSanitizer.sanitizeForLog(command.responseText(), false);
        AiSanitizedContent error = aiContentSanitizer.sanitizeForLog(resolveErrorMessage(command), true);
        AiCostEstimate cost = aiCostCalculator.estimate(
                command.attempts(), command.actualModel(), command.usage());

        LambdaUpdateWrapper<AiCallLog> wrapper = new LambdaUpdateWrapper<AiCallLog>()
                .eq(AiCallLog::getId, command.logId())
                .eq(AiCallLog::getStatus, AiCallLogStatusEnum.RUNNING.getValue())
                .set(AiCallLog::getStatus, command.status().getValue())
                .set(AiCallLog::getResponseText, response.value())
                .set(AiCallLog::getErrorMessage, error.value())
                .set(AiCallLog::getResponseSanitizationStatus, response.status().name())
                .set(AiCallLog::getErrorSanitizationStatus, error.status().name())
                .set(AiCallLog::getResponseTruncated, response.truncated() ? 1 : 0)
                .set(AiCallLog::getErrorTruncated, error.truncated() ? 1 : 0)
                .set(AiCallLog::getResponseHash, response.sha256())
                .set(AiCallLog::getErrorHash, error.sha256())
                .set(AiCallLog::getCostTimeMs, command.costTimeMs())
                .set(AiCallLog::getFallbackUsed, command.modelFallbackUsed() ? 1 : 0)
                .set(AiCallLog::getDegraded, command.degraded() ? 1 : 0);

        setIfNotBlank(wrapper, AiCallLog::getRequestedModel, command.requestedModel());
        setIfNotBlank(wrapper, AiCallLog::getModelName, command.actualModel());
        setIfNotBlank(wrapper, AiCallLog::getFinishReason, command.finishReason());
        setIfNotBlank(wrapper, AiCallLog::getProviderRequestId, command.providerRequestId());
        setIfNotBlank(wrapper, AiCallLog::getTraceId, command.traceId());
        if (command.retryCount() != null) {
            wrapper.set(AiCallLog::getRetryCount, Math.max(command.retryCount(), 0));
        }
        if (command.usage() != null) {
            wrapper.set(AiCallLog::getPromptTokens, nonNegative(command.usage().promptTokens()));
            wrapper.set(AiCallLog::getCompletionTokens, nonNegative(command.usage().completionTokens()));
            wrapper.set(AiCallLog::getTotalTokens, nonNegative(command.usage().totalTokens()));
        }
        if (cost.estimatedCost() != null) {
            wrapper.set(AiCallLog::getPriceVersion, cost.priceVersion());
            wrapper.set(AiCallLog::getCurrency, cost.currency());
            wrapper.set(AiCallLog::getEstimatedCost, cost.estimatedCost());
        }
        if (command.modelFallbackReason() != null) {
            wrapper.set(AiCallLog::getFallbackReason, command.modelFallbackReason().name());
        }
        if (command.failureType() != null) {
            wrapper.set(AiCallLog::getFailureType, command.failureType().name());
        }
        boolean updated = aiCallLogMapper.update(null, wrapper) == 1;
        if (updated && aiMetricsRecorder != null) {
            try {
                AiCallLog existing = aiCallLogMapper.selectById(command.logId());
                aiMetricsRecorder.recordInvocation(existing == null ? "unknown" : existing.getScene(), command, cost);
            } catch (RuntimeException metricsFailure) {
                // Observability is best-effort and must never change a persisted AI terminal state.
            }
        }
        return updated;
    }

    private String resolveErrorMessage(AiCallLogCompletionCommand command) {
        return StrUtil.isNotBlank(command.errorMessage())
                ? command.errorMessage()
                : command.degradationReason();
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
    public void updateProtocolMetadata(Long logId, AiChatResult result, String traceId) {
        if (logId == null || logId <= 0 || result == null) {
            return;
        }
        LambdaUpdateWrapper<AiCallLog> wrapper = new LambdaUpdateWrapper<AiCallLog>()
                .eq(AiCallLog::getId, logId);
        if (StrUtil.isNotBlank(result.requestedModel())) {
            wrapper.set(AiCallLog::getRequestedModel, result.requestedModel().trim());
        }
        if (StrUtil.isNotBlank(result.actualModel())) {
            wrapper.set(AiCallLog::getModelName, result.actualModel().trim());
        }
        if (StrUtil.isNotBlank(result.finishReason())) {
            wrapper.set(AiCallLog::getFinishReason, result.finishReason().trim());
        }
        if (StrUtil.isNotBlank(result.providerRequestId())) {
            wrapper.set(AiCallLog::getProviderRequestId, result.providerRequestId().trim());
        }
        if (result.usage() != null) {
            wrapper.set(AiCallLog::getPromptTokens, nonNegative(result.usage().promptTokens()));
            wrapper.set(AiCallLog::getCompletionTokens, nonNegative(result.usage().completionTokens()));
            wrapper.set(AiCallLog::getTotalTokens, nonNegative(result.usage().totalTokens()));
        }
        AiCostEstimate cost = aiCostCalculator.estimate(result.actualModel(), result.usage());
        if (cost.estimatedCost() != null) {
            wrapper.set(AiCallLog::getPriceVersion, cost.priceVersion());
            wrapper.set(AiCallLog::getCurrency, cost.currency());
            wrapper.set(AiCallLog::getEstimatedCost, cost.estimatedCost());
        }
        wrapper.set(AiCallLog::getFallbackUsed, result.fallbackUsed() ? 1 : 0);
        if (result.fallbackReason() != null) {
            wrapper.set(AiCallLog::getFallbackReason, result.fallbackReason().name());
        }
        if (StrUtil.isNotBlank(traceId)) {
            wrapper.set(AiCallLog::getTraceId, traceId.trim());
        }
        aiCallLogMapper.update(null, wrapper);
    }

    private Long nonNegative(Integer value) {
        return value == null ? null : Math.max(value.longValue(), 0L);
    }

    private void setIfNotBlank(LambdaUpdateWrapper<AiCallLog> wrapper,
                               com.baomidou.mybatisplus.core.toolkit.support.SFunction<AiCallLog, ?> column,
                               String value) {
        if (StrUtil.isNotBlank(value)) {
            wrapper.set(column, value.trim());
        }
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

        AiSanitizedContent response = aiContentSanitizer.sanitizeForLog(responseText, false);
        AiSanitizedContent error = aiContentSanitizer.sanitizeForLog(errorMessage, true);

        aiCallLogMapper.update(null, new LambdaUpdateWrapper<AiCallLog>()
                .eq(AiCallLog::getId, logId)
                .eq(AiCallLog::getStatus, AiCallLogStatusEnum.RUNNING.getValue())
                .set(AiCallLog::getStatus, status)
                .set(AiCallLog::getResponseText, response.value())
                .set(AiCallLog::getErrorMessage, error.value())
                .set(AiCallLog::getResponseSanitizationStatus, response.status().name())
                .set(AiCallLog::getErrorSanitizationStatus, error.status().name())
                .set(AiCallLog::getResponseTruncated, response.truncated() ? 1 : 0)
                .set(AiCallLog::getErrorTruncated, error.truncated() ? 1 : 0)
                .set(AiCallLog::getResponseHash, response.sha256())
                .set(AiCallLog::getErrorHash, error.sha256())
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
        vo.setTotalPromptTokens(sumLong(callLogs, AiCallLog::getPromptTokens));
        vo.setTotalCompletionTokens(sumLong(callLogs, AiCallLog::getCompletionTokens));
        vo.setTotalTokens(sumLong(callLogs, AiCallLog::getTotalTokens));
        vo.setTotalEstimatedCost(totalEstimatedCost(callLogs));
        vo.setUnknownUsageCount(callLogs.stream().filter(log -> log.getTotalTokens() == null).count());
        vo.setFallbackCount(callLogs.stream().filter(log -> Objects.equals(log.getFallbackUsed(), 1)).count());
        vo.setDegradedCount(callLogs.stream().filter(log -> Objects.equals(log.getDegraded(), 1)).count());
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
        vo.setRequestedModel(callLog.getRequestedModel());
        vo.setActualModel(callLog.getModelName());
        vo.setTraceId(callLog.getTraceId());
        vo.setProviderRequestId(callLog.getProviderRequestId());
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
        populateGovernanceFields(vo, callLog);
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
        vo.setRequestedModel(callLog.getRequestedModel());
        vo.setActualModel(callLog.getModelName());
        vo.setTraceId(callLog.getTraceId());
        vo.setProviderRequestId(callLog.getProviderRequestId());
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
        vo.setPromptTokens(callLog.getPromptTokens());
        vo.setCompletionTokens(callLog.getCompletionTokens());
        vo.setTotalTokens(callLog.getTotalTokens());
        vo.setPriceVersion(callLog.getPriceVersion());
        vo.setCurrency(callLog.getCurrency());
        vo.setEstimatedCost(callLog.getEstimatedCost());
        vo.setFallbackUsed(callLog.getFallbackUsed());
        vo.setDegraded(callLog.getDegraded());
        vo.setFailureType(callLog.getFailureType());
        vo.setRequestSanitizationStatus(callLog.getRequestSanitizationStatus());
        vo.setResponseSanitizationStatus(callLog.getResponseSanitizationStatus());
        vo.setErrorSanitizationStatus(callLog.getErrorSanitizationStatus());
        vo.setRequestTruncated(callLog.getRequestTruncated());
        vo.setResponseTruncated(callLog.getResponseTruncated());
        vo.setErrorTruncated(callLog.getErrorTruncated());
        vo.setRequestHash(callLog.getRequestHash());
        vo.setResponseHash(callLog.getResponseHash());
        vo.setErrorHash(callLog.getErrorHash());
        vo.setCreateTime(callLog.getCreateTime());
        vo.setUpdateTime(callLog.getUpdateTime());
        return vo;
    }

    private void populateGovernanceFields(AiCallLogVO vo, AiCallLog callLog) {
        vo.setPromptTokens(callLog.getPromptTokens());
        vo.setCompletionTokens(callLog.getCompletionTokens());
        vo.setTotalTokens(callLog.getTotalTokens());
        vo.setPriceVersion(callLog.getPriceVersion());
        vo.setCurrency(callLog.getCurrency());
        vo.setEstimatedCost(callLog.getEstimatedCost());
        vo.setFallbackUsed(callLog.getFallbackUsed());
        vo.setDegraded(callLog.getDegraded());
        vo.setRequestSanitizationStatus(callLog.getRequestSanitizationStatus());
        vo.setResponseSanitizationStatus(callLog.getResponseSanitizationStatus());
        vo.setErrorSanitizationStatus(callLog.getErrorSanitizationStatus());
        vo.setRequestTruncated(callLog.getRequestTruncated());
        vo.setResponseTruncated(callLog.getResponseTruncated());
        vo.setErrorTruncated(callLog.getErrorTruncated());
    }

    private long sumLong(List<AiCallLog> callLogs,
                         java.util.function.Function<AiCallLog, Long> extractor) {
        return callLogs.stream().map(extractor).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private BigDecimal totalEstimatedCost(List<AiCallLog> callLogs) {
        List<BigDecimal> knownCosts = callLogs.stream()
                .map(AiCallLog::getEstimatedCost)
                .filter(Objects::nonNull)
                .toList();
        if (knownCosts.isEmpty()) {
            return null;
        }
        return knownCosts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
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

    private String defaultIfBlankNullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Override
    public AiCallLog latestMetadata() {
        return aiCallLogMapper.selectOne(new LambdaQueryWrapper<AiCallLog>()
                .select(AiCallLog::getStatus, AiCallLog::getCreateTime)
                .orderByDesc(AiCallLog::getCreateTime).last("limit 1"));
    }

    @Override
    public BigDecimal sumEstimatedCost(LocalDateTime from, LocalDateTime to) {
        return aiCallLogMapper.sumEstimatedCost(from, to);
    }

    @Override
    public long countBodyCleanupCandidates(LocalDateTime cutoff) {
        return aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .ne(AiCallLog::getStatus, AiCallLogStatusEnum.RUNNING.getValue())
                .isNull(AiCallLog::getBodyPurgedAt).lt(AiCallLog::getCreateTime, cutoff));
    }

    @Override
    public long countMetadataCleanupCandidates(LocalDateTime cutoff) {
        return aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .ne(AiCallLog::getStatus, AiCallLogStatusEnum.RUNNING.getValue())
                .lt(AiCallLog::getCreateTime, cutoff));
    }

    @Override
    public CleanupBatchResult purgeBodyBatch(LocalDateTime cutoff, long cursor, int batchSize) {
        List<Long> ids = aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .select(AiCallLog::getId).gt(AiCallLog::getId, cursor)
                .ne(AiCallLog::getStatus, AiCallLogStatusEnum.RUNNING.getValue())
                .isNull(AiCallLog::getBodyPurgedAt).lt(AiCallLog::getCreateTime, cutoff)
                .orderByAsc(AiCallLog::getId).last("limit " + batchSize))
                .stream().map(AiCallLog::getId).toList();
        int affected = ids.isEmpty() ? 0 : aiCallLogMapper.update(null, new LambdaUpdateWrapper<AiCallLog>()
                .in(AiCallLog::getId, ids).isNull(AiCallLog::getBodyPurgedAt)
                .set(AiCallLog::getRequestText, null).set(AiCallLog::getResponseText, null)
                .set(AiCallLog::getErrorMessage, null).set(AiCallLog::getBodyPurgedAt, LocalDateTime.now()));
        return cleanupResult(ids, affected, affected, 0, batchSize);
    }

    @Override
    public CleanupBatchResult deleteMetadataBatch(LocalDateTime cutoff, long cursor, int batchSize) {
        List<Long> ids = aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .select(AiCallLog::getId).gt(AiCallLog::getId, cursor)
                .ne(AiCallLog::getStatus, AiCallLogStatusEnum.RUNNING.getValue())
                .lt(AiCallLog::getCreateTime, cutoff).orderByAsc(AiCallLog::getId)
                .last("limit " + batchSize)).stream().map(AiCallLog::getId).toList();
        int affected = ids.isEmpty() ? 0 : aiCallLogMapper.deleteByIds(ids);
        return cleanupResult(ids, affected, 0, affected, batchSize);
    }

    private CleanupBatchResult cleanupResult(List<Long> ids, long affected,
                                             long redacted, long deleted, int batchSize) {
        long next = ids.isEmpty() ? 0L : ids.get(ids.size() - 1);
        return new CleanupBatchResult(ids.size(), affected, redacted, deleted,
                next, ids.size() < batchSize);
    }
}
