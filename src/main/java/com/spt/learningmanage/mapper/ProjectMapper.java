package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /** Reads a logically deleted project for the recovery workflow. */
    @Select("SELECT * FROM project WHERE id = #{id} AND deleted_at IS NOT NULL LIMIT 1")
    Project selectDeletedById(@Param("id") Long id);
}
