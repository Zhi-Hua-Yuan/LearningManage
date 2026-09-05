package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiKnowledgeIndexEventMapper extends BaseMapper<AiKnowledgeIndexEvent> {

    @Select("""
            SELECT *
            FROM ai_knowledge_index_event
            WHERE (
                    status = 'PENDING'
                    OR (status = 'RETRY_WAIT' AND (next_attempt_at IS NULL OR next_attempt_at <= #{now}))
                    OR (status = 'PROCESSING' AND lease_until < #{now})
                  )
            ORDER BY id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<AiKnowledgeIndexEvent> selectReadyForUpdate(@Param("now") LocalDateTime now,
                                                     @Param("limit") int limit);
}
