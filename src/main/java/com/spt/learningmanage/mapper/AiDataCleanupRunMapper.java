package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiDataCleanupRunMapper extends BaseMapper<AiDataCleanupRun> {
    @Select("SELECT id FROM ai_data_cleanup_lock WHERE id=1 FOR UPDATE")
    Integer lockSubmission();

    @Select("""
            SELECT * FROM ai_data_cleanup_run
            WHERE initiator_user_id <=> #{userId} AND client_request_id=#{clientRequestId}
            LIMIT 1 FOR UPDATE
            """)
    AiDataCleanupRun selectByRequestForUpdate(@Param("userId") Long userId,
                                              @Param("clientRequestId") String clientRequestId);

    @Select("""
            SELECT * FROM ai_data_cleanup_run
            WHERE status IN ('PENDING','RUNNING')
            ORDER BY create_time,id LIMIT 1 FOR UPDATE
            """)
    AiDataCleanupRun selectActiveForUpdate();

    @Select("""
            SELECT * FROM ai_data_cleanup_run
            WHERE run_id=#{runId} AND dry_run=1
            LIMIT 1 FOR UPDATE
            """)
    AiDataCleanupRun selectDryRunForUpdate(@Param("runId") String runId);

    @Select("""
            SELECT COUNT(*) FROM ai_data_cleanup_run
            WHERE approved_dry_run_id=#{approvedDryRunId} AND dry_run=0
            """)
    long countFormalByApprovedDryRunId(@Param("approvedDryRunId") Long approvedDryRunId);

    @Select("""
            SELECT * FROM ai_data_cleanup_run
            WHERE status = 'PENDING' OR (status = 'RUNNING' AND lease_until < #{now})
            ORDER BY create_time, id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    AiDataCleanupRun selectClaimableForUpdate(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_data_cleanup_run
            SET status='RUNNING', worker_id=#{workerId}, execution_token=#{token},
                lease_until=#{leaseUntil}, heartbeat_at=#{now},
                started_at=COALESCE(started_at, #{now}), error_summary=NULL
            WHERE id=#{id} AND (status='PENDING' OR (status='RUNNING' AND lease_until < #{now}))
            """)
    int claim(@Param("id") Long id, @Param("workerId") String workerId,
              @Param("token") String token, @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE ai_data_cleanup_run
            SET heartbeat_at=#{now}, lease_until=#{leaseUntil}
            WHERE id=#{id} AND status='RUNNING' AND execution_token=#{token}
            """)
    int heartbeat(@Param("id") Long id, @Param("token") String token,
                  @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE ai_data_cleanup_run
            SET status=#{status}, scanned_count=#{scanned}, estimated_count=#{estimated},
                affected_count=#{affected}, failure_count=#{failures}, error_summary=#{error},
                finished_at=#{now}, worker_id=NULL, execution_token=NULL, lease_until=NULL
            WHERE id=#{id} AND status='RUNNING' AND execution_token=#{token}
            """)
    int complete(@Param("id") Long id, @Param("token") String token,
                 @Param("status") String status, @Param("scanned") long scanned,
                 @Param("estimated") long estimated, @Param("affected") long affected,
                 @Param("failures") long failures, @Param("error") String error,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_data_cleanup_run
            SET status='CANCELED', canceled_at=#{now}, finished_at=#{now}
            WHERE id=#{id} AND status='PENDING'
            """)
    int cancelPending(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_data_cleanup_run SET cancel_requested_at=#{now}
            WHERE id=#{id} AND status='RUNNING' AND cancel_requested_at IS NULL
            """)
    int requestCancelRunning(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_data_cleanup_run
            SET status='PENDING', worker_id=NULL, execution_token=NULL, lease_until=NULL
            WHERE id=#{id} AND status='RUNNING' AND execution_token=#{token}
              AND cancel_requested_at IS NULL
            """)
    int releaseForResume(@Param("id") Long id, @Param("token") String token);
}
