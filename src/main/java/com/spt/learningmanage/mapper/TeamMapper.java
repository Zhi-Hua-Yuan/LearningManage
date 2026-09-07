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
    @Select("SELECT id, name, description, owner_id, invite_code, create_time, update_time, " +
            "deleted_at, is_delete FROM team WHERE id = #{id} AND is_delete = 0 " +
            "AND deleted_at IS NULL FOR UPDATE")
    Team selectActiveByIdForUpdate(@Param("id") Long id);

    @Select("SELECT id, name, description, owner_id, invite_code, create_time, update_time, " +
            "deleted_at, is_delete FROM team WHERE invite_code = #{inviteCode} AND is_delete = 0 " +
            "AND deleted_at IS NULL FOR UPDATE")
    Team selectActiveByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    @Update("UPDATE team SET is_delete = 1, deleted_at = #{deletedAt} " +
            "WHERE id = #{id} AND is_delete = 0 AND deleted_at IS NULL")
    int deactivateTeamCas(@Param("id") Long id, @Param("deletedAt") java.time.LocalDateTime deletedAt);

    @Update("UPDATE team SET data_version = data_version + 1 WHERE id = #{id}")
    int incrementDataVersion(@Param("id") Long id);

    @Select("SELECT data_version FROM team WHERE id = #{id} LIMIT 1")
    Long selectDataVersion(@Param("id") Long id);
}
