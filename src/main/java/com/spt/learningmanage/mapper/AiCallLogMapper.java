package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
    @Update("UPDATE ai_call_log SET agent_run_id = #{agentRunId}, agent_round_no = #{agentRoundNo} WHERE id = #{id}")
    int linkAgentRound(@Param("id") Long id,
                       @Param("agentRunId") String agentRunId,
                       @Param("agentRoundNo") Integer agentRoundNo);

    @Select("""
            SELECT SUM(estimated_cost) FROM ai_call_log
            WHERE create_time >= #{from} AND create_time <= #{to}
            """)
    BigDecimal sumEstimatedCost(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
}
