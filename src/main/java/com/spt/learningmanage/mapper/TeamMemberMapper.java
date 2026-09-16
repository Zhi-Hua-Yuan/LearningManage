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
     * 锁定参与成员关系终止决策的有效成员记录。
     * 实现按关系 id 排序，便于调用方对操作者与目标记录使用确定性的加锁顺序。
     */
    List<TeamMember> selectActiveMembersForUpdate(
            @Param("teamId") Long teamId,
            @Param("userIds") Collection<Long> userIds
    );

    List<TeamMember> selectAllActiveMembersForUpdate(@Param("teamId") Long teamId);

    int deactivateAllActiveMemberships(
            @Param("teamId") Long teamId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * 当角色与有效状态仍与加锁快照一致时，以原子方式将单条成员关系置为失效。
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
