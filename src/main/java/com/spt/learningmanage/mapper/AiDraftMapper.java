package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiDraftMapper extends BaseMapper<AiDraft> {

    @Select("""
            SELECT *
            FROM ai_draft
            WHERE user_id = #{userId}
              AND draft_id = #{draftId}
              AND scene = #{scene}
            LIMIT 1
            FOR UPDATE
            """)
    AiDraft selectForUpdate(@Param("userId") Long userId,
                            @Param("draftId") String draftId,
                            @Param("scene") String scene);
}
