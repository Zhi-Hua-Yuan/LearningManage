package com.spt.learningmanage.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 只用于校验初始负责人是否仍是有效团队成员。 */
@Mapper
public interface TaskAssigneeQueryMapper {

    @Select("""
            SELECT tm.user_id
            FROM team_member tm
            INNER JOIN `user` u ON u.id = tm.user_id
            WHERE tm.team_id = #{teamId}
              AND tm.user_id = #{userId}
              AND tm.is_delete = 0
              AND tm.deleted_at IS NULL
              AND u.is_delete = 0
            LIMIT 1
            FOR UPDATE
            """)
    Long selectActiveTeamAssigneeForUpdate(@Param("teamId") Long teamId, @Param("userId") Long userId);

    @Select("""
            SELECT COUNT(1)
            FROM team_member tm
            INNER JOIN `user` u ON u.id = tm.user_id
            WHERE tm.team_id = #{teamId}
              AND tm.user_id = #{userId}
              AND tm.is_delete = 0
              AND tm.deleted_at IS NULL
              AND u.is_delete = 0
            """)
    int countActiveTeamAssignee(@Param("teamId") Long teamId, @Param("userId") Long userId);
}
