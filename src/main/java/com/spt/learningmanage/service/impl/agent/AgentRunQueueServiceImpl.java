package com.spt.learningmanage.service.impl.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.service.agent.AgentRunCompletion;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AgentRunQueueServiceImpl implements AgentRunQueueService {
    private final AiAgentRunMapper runMapper;
    private final AgentProperties properties;

    public AgentRunQueueServiceImpl(AiAgentRunMapper runMapper, AgentProperties properties) {
        this.runMapper = runMapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    public List<AiAgentRun> claimReady(String workerId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        runMapper.failExhaustedLeases(now, properties.getMaxAttempts());
        List<AiAgentRun> claimed = new ArrayList<>();
        for (AiAgentRun candidate : runMapper.selectClaimableForUpdate(
                now, properties.getMaxAttempts(), Math.max(1, limit))) {
            String token = UUID.randomUUID().toString();
            if (runMapper.claim(candidate.getId(), workerId, token, now,
                    now.plusSeconds(properties.getLeaseSeconds()), properties.getMaxAttempts()) == 1) {
                candidate.setStatus("RUNNING");
                candidate.setWorkerId(workerId);
                candidate.setExecutionToken(token);
                candidate.setAttemptCount((candidate.getAttemptCount() == null ? 0 : candidate.getAttemptCount()) + 1);
                claimed.add(candidate);
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    public boolean heartbeat(AiAgentRun run) {
        LocalDateTime now = LocalDateTime.now();
        return runMapper.heartbeat(run.getId(), run.getExecutionToken(), now,
                now.plusSeconds(properties.getLeaseSeconds())) == 1;
    }

    @Override
    public boolean updateProgress(AiAgentRun run, String step, int toolCount, long dataVersion) {
        return runMapper.updateProgress(run.getId(), run.getExecutionToken(), step, toolCount, dataVersion) == 1;
    }

    @Override
    public boolean cancellationRequested(String runId, String executionToken) {
        AiAgentRun current = runMapper.selectOne(new LambdaQueryWrapper<AiAgentRun>()
                .eq(AiAgentRun::getRunId, runId)
                .eq(AiAgentRun::getExecutionToken, executionToken)
                .last("limit 1"));
        return current == null || current.getCancelRequestedAt() != null;
    }

    @Override
    public boolean complete(AiAgentRun run, AgentRunCompletion value) {
        return runMapper.complete(run.getId(), run.getExecutionToken(), value.terminalStatus(),
                run.getOrchestrationMode(), value.step(),
                value.endDataVersion(), value.draftId(), value.aiCallLogId(), value.partialReason(),
                value.failureType(), value.errorSummary(), value.model(), value.promptCode(), value.promptVersion(),
                LocalDateTime.now()) == 1;
    }
}
