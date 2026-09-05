package com.spt.learningmanage.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiKnowledgeSourceLockMapper {

    @Insert("""
            INSERT IGNORE INTO ai_knowledge_source_lock(source_type, source_id)
            VALUES(#{sourceType}, #{sourceId})
            """)
    int ensureRow(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    @Update("""
            UPDATE ai_knowledge_source_lock
            SET owner_token = #{token}, lease_until = #{leaseUntil}
            WHERE source_type = #{sourceType}
              AND source_id = #{sourceId}
              AND (owner_token IS NULL OR lease_until < #{now} OR owner_token = #{token})
            """)
    int acquire(@Param("sourceType") String sourceType,
                @Param("sourceId") Long sourceId,
                @Param("token") String token,
                @Param("now") LocalDateTime now,
                @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE ai_knowledge_source_lock
            SET lease_until = #{leaseUntil}
            WHERE source_type = #{sourceType}
              AND source_id = #{sourceId}
              AND owner_token = #{token}
              AND lease_until >= #{now}
            """)
    int renew(@Param("sourceType") String sourceType,
              @Param("sourceId") Long sourceId,
              @Param("token") String token,
              @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    @Select("""
            SELECT COUNT(*)
            FROM ai_knowledge_source_lock
            WHERE source_type = #{sourceType}
              AND source_id = #{sourceId}
              AND owner_token = #{token}
              AND lease_until >= #{now}
            """)
    int isOwned(@Param("sourceType") String sourceType,
                @Param("sourceId") Long sourceId,
                @Param("token") String token,
                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_knowledge_source_lock
            SET owner_token = NULL, lease_until = NULL
            WHERE source_type = #{sourceType}
              AND source_id = #{sourceId}
              AND owner_token = #{token}
            """)
    int release(@Param("sourceType") String sourceType,
                @Param("sourceId") Long sourceId,
                @Param("token") String token);
}
