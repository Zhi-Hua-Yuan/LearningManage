package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.Team;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 团队 Mapper
 */
public interface TeamMapper extends BaseMapper<Team> {
    @Update("UPDATE team SET data_version = data_version + 1 WHERE id = #{id}")
    int incrementDataVersion(@Param("id") Long id);

    @Select("SELECT data_version FROM team WHERE id = #{id} LIMIT 1")
    Long selectDataVersion(@Param("id") Long id);
}
