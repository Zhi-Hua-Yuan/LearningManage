package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiAgentRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiAgentRunMapper extends BaseMapper<AiAgentRun> {
    @Select("""
            SELECT * FROM ai_agent_run
            WHERE (status = 'PENDING'
                OR (status = 'RUNNING' AND lease_until < #{now} AND attempt_count < #{maxAttempts}))
            ORDER BY create_time, id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<AiAgentRun> selectClaimableForUpdate(@Param("now") LocalDateTime now,
                                               @Param("maxAttempts") int maxAttempts,
                                               @Param("limit") int limit);

    @Update("""
            UPDATE ai_agent_run
            SET status = 'RUNNING', worker_id = #{workerId}, execution_token = #{executionToken},
                attempt_count = attempt_count + 1, lease_until = #{leaseUntil}, heartbeat_at = #{now},
                started_at = COALESCE(started_at, #{now}), current_step = 'CLAIMED',
                failure_type = NULL, error_summary = NULL
            WHERE id = #{id}
              AND (status = 'PENDING' OR (status = 'RUNNING' AND lease_until < #{now}))
              AND attempt_count < #{maxAttempts}
            """)
    int claim(@Param("id") Long id,
              @Param("workerId") String workerId,
              @Param("executionToken") String executionToken,
              @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE ai_agent_run
            SET status = 'FAILED', failure_type = 'AGENT_WORKER_LOST',
                error_summary = 'Agent Worker lease expired', finished_at = #{now},
                worker_id = NULL, execution_token = NULL, lease_until = NULL
            WHERE status = 'RUNNING' AND lease_until < #{now} AND attempt_count >= #{maxAttempts}
            """)
    int failExhaustedLeases(@Param("now") LocalDateTime now,
                            @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE ai_agent_run
            SET heartbeat_at = #{now}, lease_until = #{leaseUntil}
            WHERE id = #{id} AND status = 'RUNNING' AND execution_token = #{executionToken}
            """)
    int heartbeat(@Param("id") Long id,
                  @Param("executionToken") String executionToken,
                  @Param("now") LocalDateTime now,
                  @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE ai_agent_run
            SET current_step = #{step}, tool_count = #{toolCount},
                start_data_version = COALESCE(start_data_version, #{dataVersion})
            WHERE id = #{id} AND status = 'RUNNING' AND execution_token = #{executionToken}
            """)
    int updateProgress(@Param("id") Long id,
                       @Param("executionToken") String executionToken,
                       @Param("step") String step,
                       @Param("toolCount") int toolCount,
                       @Param("dataVersion") Long dataVersion);

    @Update("""
            UPDATE ai_agent_run
            SET cancel_requested_at = #{now}
            WHERE id = #{id} AND status = 'RUNNING' AND cancel_requested_at IS NULL
            """)
    int requestRunningCancellation(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_agent_run
            SET status = 'CANCELED', cancel_requested_at = #{now}, finished_at = #{now}, current_step = 'CANCELED'
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int cancelPending(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_agent_run
            SET status = #{terminalStatus}, orchestration_mode = #{orchestrationMode},
                current_step = #{step}, end_data_version = #{endDataVersion},
                draft_id = #{draftId}, ai_call_log_id = #{aiCallLogId}, partial_reason = #{partialReason},
                failure_type = #{failureType}, error_summary = #{errorSummary}, model = #{model},
                prompt_code = #{promptCode}, prompt_version = #{promptVersion}, finished_at = #{now},
                worker_id = NULL, lease_until = NULL, heartbeat_at = #{now}
            WHERE id = #{id} AND status = 'RUNNING' AND execution_token = #{executionToken}
            """)
    int complete(@Param("id") Long id,
                 @Param("executionToken") String executionToken,
                 @Param("terminalStatus") String terminalStatus,
                 @Param("orchestrationMode") String orchestrationMode,
                 @Param("step") String step,
                 @Param("endDataVersion") Long endDataVersion,
                 @Param("draftId") String draftId,
                 @Param("aiCallLogId") Long aiCallLogId,
                 @Param("partialReason") String partialReason,
                 @Param("failureType") String failureType,
                 @Param("errorSummary") String errorSummary,
                 @Param("model") String model,
                 @Param("promptCode") String promptCode,
                 @Param("promptVersion") Integer promptVersion,
                 @Param("now") LocalDateTime now);
}
