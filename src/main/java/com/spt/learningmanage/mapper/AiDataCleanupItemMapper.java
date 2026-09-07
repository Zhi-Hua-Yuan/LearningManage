package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiDataCleanupItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiDataCleanupItemMapper extends BaseMapper<AiDataCleanupItem> {
    @Update("""
            UPDATE ai_data_cleanup_item item
            JOIN ai_data_cleanup_run run ON run.run_id=item.run_id
            SET item.cursor_id=#{cursor}, item.scanned_count=#{scanned},
                item.estimated_count=#{estimated},
                item.redacted_count=#{redacted}, item.deleted_count=#{deleted},
                item.status=#{status}, item.finished_at=#{finishedAt}
            WHERE item.id=#{itemId} AND run.id=#{runId}
              AND run.status='RUNNING' AND run.execution_token=#{token}
              AND run.lease_until >= NOW(3)
            """)
    int updateProgressFenced(@Param("itemId") Long itemId,
                             @Param("runId") Long runId,
                             @Param("token") String token,
                             @Param("cursor") long cursor,
                             @Param("scanned") long scanned,
                             @Param("estimated") long estimated,
                             @Param("redacted") long redacted,
                             @Param("deleted") long deleted,
                             @Param("status") String status,
                             @Param("finishedAt") LocalDateTime finishedAt);

    @Update("""
            UPDATE ai_data_cleanup_item item
            JOIN ai_data_cleanup_run run ON run.run_id=item.run_id
            SET item.status='FAILED', item.error_summary=#{error}, item.finished_at=#{now}
            WHERE item.id=#{itemId} AND item.status='RUNNING' AND run.id=#{runId}
              AND run.status='RUNNING' AND run.execution_token=#{token}
              AND run.lease_until >= NOW(3)
            """)
    int failFenced(@Param("itemId") Long itemId,
                   @Param("runId") Long runId,
                   @Param("token") String token,
                   @Param("error") String error,
                   @Param("now") LocalDateTime now);
}
