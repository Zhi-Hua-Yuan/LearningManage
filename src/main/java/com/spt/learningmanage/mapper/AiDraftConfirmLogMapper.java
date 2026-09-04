package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiDraftConfirmLogMapper extends BaseMapper<AiDraftConfirmLog> {

    @Select("""
            SELECT *
            FROM ai_draft_confirm_log
            WHERE user_id = #{userId}
              AND draft_id = #{draftId}
            LIMIT 1
            """)
    AiDraftConfirmLog selectByUserAndDraft(@Param("userId") Long userId,
                                           @Param("draftId") String draftId);
}
