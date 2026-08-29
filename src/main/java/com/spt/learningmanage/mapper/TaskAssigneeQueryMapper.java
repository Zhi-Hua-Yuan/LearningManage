package com.spt.learningmanage.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 在事务内锁定并校验团队任务负责人是否仍是有效团队成员。 */
@Mapper
public interface TaskAssigneeQueryMapper {

    @Select("""
            SELECT tm.user_id
            FROM team_member tm
            WHERE tm.team_id = #{teamId}
              AND tm.user_id = #{userId}
              AND tm.is_delete = 0
              AND tm.deleted_at IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM `user` u
                  WHERE u.id = tm.user_id
                    AND u.is_delete = 0
              )
            LIMIT 1
            FOR UPDATE
            """)
    Long selectActiveTeamAssigneeForUpdate(@Param("teamId") Long teamId, @Param("userId") Long userId);
}
