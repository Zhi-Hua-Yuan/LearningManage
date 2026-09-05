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
            ORDER BY id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    AiKnowledgeBackfillRun selectReadyForUpdate(@Param("now") LocalDateTime now);
}
