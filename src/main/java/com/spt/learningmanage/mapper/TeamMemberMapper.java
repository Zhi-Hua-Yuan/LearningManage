package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.TeamMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 团队成员关系 Mapper
 */
public interface TeamMemberMapper extends BaseMapper<TeamMember> {

    /**
     * Lock the active membership rows that participate in a membership
     * termination decision. The implementation orders by relationship id so
     * callers can use one deterministic lock order for actor and target rows.
     */
    List<TeamMember> selectActiveMembersForUpdate(
            @Param("teamId") Long teamId,
            @Param("userIds") Collection<Long> userIds
    );

    /**
     * Atomically invalidate one membership row when its role and active state
     * still match the locked snapshot.
     */
    int deactivateMembershipCas(
            @Param("membershipId") Long membershipId,
            @Param("teamId") Long teamId,
            @Param("userId") Long userId,
            @Param("expectedRole") String expectedRole,
            @Param("deletedAt") LocalDateTime deletedAt
    );

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
}
