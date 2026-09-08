package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    @Select("SELECT * FROM project WHERE id = #{id} AND is_delete = 0 " +
            "AND deleted_at IS NULL FOR UPDATE")
    Project selectActiveByIdForUpdate(@Param("id") Long id);

    @Select("SELECT order_no FROM project WHERE user_id = #{userId} AND team_id IS NULL " +
            "AND is_delete = 0 AND deleted_at IS NULL ORDER BY order_no DESC LIMIT 1 FOR UPDATE")
    Integer selectMaxPersonalOrderNoForUpdate(@Param("userId") Long userId);

    @Select("SELECT order_no FROM project WHERE team_id = #{teamId} " +
            "AND is_delete = 0 AND deleted_at IS NULL ORDER BY order_no DESC LIMIT 1 FOR UPDATE")
    Integer selectMaxTeamOrderNoForUpdate(@Param("teamId") Long teamId);

    /** Reads a logically deleted project for the recovery workflow. */
    @Select("SELECT * FROM project WHERE id = #{id} AND deleted_at IS NOT NULL LIMIT 1")
    Project selectDeletedById(@Param("id") Long id);

    @Update("UPDATE project SET data_version = data_version + 1 WHERE id = #{id}")
    int incrementDataVersion(@Param("id") Long id);

    @Select("SELECT data_version FROM project WHERE id = #{id} LIMIT 1")
    Long selectDataVersion(@Param("id") Long id);
}
