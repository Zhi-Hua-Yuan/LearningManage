package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface AiKnowledgeBackfillRunMapper extends BaseMapper<AiKnowledgeBackfillRun> {

    @Select("""
            SELECT *
            FROM ai_knowledge_backfill_run
            WHERE status = 'PENDING'
               OR (status = 'RUNNING' AND lease_until < #{now})
            ORDER BY CASE WHEN run_key = 'stage5-qdrant-numeric-payload-v1' THEN 1 ELSE 0 END, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    AiKnowledgeBackfillRun selectReadyForUpdate(@Param("now") LocalDateTime now);
}
