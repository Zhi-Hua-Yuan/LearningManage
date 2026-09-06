package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
    @Update("UPDATE ai_call_log SET agent_run_id = #{agentRunId}, agent_round_no = #{agentRoundNo} WHERE id = #{id}")
    int linkAgentRound(@Param("id") Long id,
                       @Param("agentRunId") String agentRunId,
                       @Param("agentRoundNo") Integer agentRoundNo);
}
