package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiAgentToolLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiAgentToolLogMapper extends BaseMapper<AiAgentToolLog> {
    @Select("SELECT COALESCE(MAX(tool_sequence), 0) FROM ai_agent_tool_log WHERE run_id=#{runId} AND attempt_no=#{attemptNo}")
    int selectMaxSequence(@Param("runId") String runId, @Param("attemptNo") Integer attemptNo);
}
