package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.TeamMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 团队成员关系 Mapper
 */
public interface TeamMemberMapper extends BaseMapper<TeamMember> {

    /**
     * 查询成员关系（包含逻辑删除记录）。
     */
    @Select("SELECT id, team_id, user_id, role, create_time, update_time, deleted_at, is_delete " +
            "FROM team_member WHERE team_id = #{teamId} AND user_id = #{userId} LIMIT 1")
    TeamMember selectIncludingDeleted(@Param("teamId") Long teamId, @Param("userId") Long userId);

    /**
     * 恢复逻辑删除的成员关系。
     */
    @Update("UPDATE team_member SET role = #{role}, is_delete = 0, deleted_at = NULL " +
            "WHERE team_id = #{teamId} AND user_id = #{userId} AND is_delete = 1")
    int restoreDeletedMember(@Param("teamId") Long teamId, @Param("userId") Long userId, @Param("role") String role);

    /**
     * 将有效成员关系逻辑删除。调用方应在同一事务内先处理关联任务。
     */
    @Update("UPDATE team_member SET is_delete = 1, deleted_at = #{deletedAt} " +
            "WHERE team_id = #{teamId} AND user_id = #{userId} AND is_delete = 0")
    int deactivateMember(@Param("teamId") Long teamId,
                         @Param("userId") Long userId,
                         @Param("deletedAt") LocalDateTime deletedAt);
}
