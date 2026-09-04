package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiReplanOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiReplanOperationMapper extends BaseMapper<AiReplanOperation> {

    @Select("""
            SELECT *
            FROM ai_replan_operation
            WHERE operation_id = #{operationId}
              AND user_id = #{userId}
              AND project_id = #{projectId}
            LIMIT 1
            FOR UPDATE
            """)
    AiReplanOperation selectForUpdate(@Param("userId") Long userId,
                                      @Param("projectId") Long projectId,
                                      @Param("operationId") String operationId);

    @Select("""
            SELECT *
            FROM ai_replan_operation
            WHERE operation_id = #{operationId}
              AND user_id = #{userId}
            LIMIT 1
            FOR UPDATE
            """)
    AiReplanOperation selectForUpdateByUser(@Param("userId") Long userId,
                                            @Param("operationId") String operationId);
}
