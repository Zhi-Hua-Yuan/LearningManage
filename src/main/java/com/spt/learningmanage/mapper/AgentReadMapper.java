package com.spt.learningmanage.mapper;

import com.spt.learningmanage.model.query.agent.AgentProjectStatsRow;
import com.spt.learningmanage.model.query.agent.AgentProjectTaskRow;
import com.spt.learningmanage.model.query.agent.AgentTeamMemberMetricsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentReadMapper {

    @Select("""
            SELECT id, title, status, priority, due_date, assignee_user_id, completed_at, update_time
            FROM task
            WHERE project_id = #{projectId} AND is_delete = 0
            ORDER BY status ASC, due_date IS NULL, due_date ASC, priority DESC, id ASC
            LIMIT #{limit}
            """)
    List<AgentProjectTaskRow> selectProjectTasks(@Param("projectId") Long projectId,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT id, title, status, priority, due_date, assignee_user_id, completed_at, update_time
            FROM task
            WHERE project_id = #{projectId} AND is_delete = 0
              AND status = 0 AND due_date < #{today}
            ORDER BY due_date ASC, priority DESC, id ASC
            LIMIT #{limit}
            """)
    List<AgentProjectTaskRow> selectOverdueProjectTasks(@Param("projectId") Long projectId,
                                                         @Param("today") LocalDate today,
                                                         @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) total_count,
              COALESCE(SUM(status BETWEEN 1 AND 3), 0) completed_count,
              COALESCE(SUM(status = 0), 0) open_count,
              COALESCE(SUM(status = 0 AND due_date < #{today}), 0) overdue_count,
              COALESCE(SUM(status = 0 AND assignee_user_id IS NULL), 0) unassigned_count,
              COALESCE(SUM(status = 0 AND due_date BETWEEN #{today} AND #{next7}), 0) due_next7_days_count,
              COALESCE(SUM(status BETWEEN 1 AND 3 AND completed_at >= #{last30}), 0) completed_last30_days_count
            FROM task
            WHERE project_id = #{projectId} AND is_delete = 0
            """)
    AgentProjectStatsRow selectProjectStats(@Param("projectId") Long projectId,
                                             @Param("today") LocalDate today,
                                             @Param("next7") LocalDate next7,
                                             @Param("last30") LocalDateTime last30);

    @Select("""
            SELECT tm.user_id,
              COALESCE(SUM(t.status = 0), 0) open_task_count,
              COALESCE(SUM(t.status = 0 AND t.due_date < #{today}), 0) overdue_open_count,
              COALESCE(SUM(t.status = 0 AND t.due_date BETWEEN #{today} AND #{next7}), 0) due_next7_days_count,
              COALESCE(SUM(t.status BETWEEN 1 AND 3 AND t.completed_at >= #{last30}), 0) completed_last30_days_count,
              COALESCE(SUM(t.status BETWEEN 1 AND 3 AND t.completed_at >= #{last30} AND t.due_date IS NOT NULL), 0) completed_with_due_date_last30_days,
              COALESCE(SUM(t.status BETWEEN 1 AND 3 AND t.completed_at >= #{last30} AND t.due_date IS NOT NULL AND DATE(t.completed_at) <= t.due_date), 0) on_time_completed_last30_days
            FROM team_member tm
            LEFT JOIN project p ON p.team_id = tm.team_id AND p.is_delete = 0
            LEFT JOIN task t ON t.project_id = p.id AND t.assignee_user_id = tm.user_id AND t.is_delete = 0
            WHERE tm.team_id = #{teamId} AND tm.is_delete = 0
            GROUP BY tm.user_id
            ORDER BY tm.user_id
            """)
    List<AgentTeamMemberMetricsRow> selectTeamMemberMetrics(@Param("teamId") Long teamId,
                                                             @Param("today") LocalDate today,
                                                             @Param("next7") LocalDate next7,
                                                             @Param("last30") LocalDateTime last30);

    @Select("""
            SELECT t.id, t.title, t.status, t.priority, t.due_date, t.assignee_user_id, t.completed_at, t.update_time
            FROM task t
            JOIN project p ON p.id = t.project_id AND p.is_delete = 0
            WHERE p.team_id = #{teamId} AND t.is_delete = 0
              AND t.status = 0 AND t.due_date < #{today}
            ORDER BY t.assignee_user_id, t.due_date ASC, t.id ASC
            LIMIT #{limit}
            """)
    List<AgentProjectTaskRow> selectTeamOverdueTasks(@Param("teamId") Long teamId,
                                                      @Param("today") LocalDate today,
                                                      @Param("limit") int limit);
}
