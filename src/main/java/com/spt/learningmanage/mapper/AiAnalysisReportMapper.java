package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiAnalysisReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiAnalysisReportMapper extends BaseMapper<AiAnalysisReport> {
    @Select("""
            <script>
            SELECT DISTINCT r.*
            FROM ai_analysis_report r
            LEFT JOIN project p ON p.id = r.project_id AND p.is_delete = 0 AND p.deleted_at IS NULL
            LEFT JOIN team pt ON pt.id = p.team_id AND pt.is_delete = 0 AND pt.deleted_at IS NULL
            LEFT JOIN team rt ON rt.id = r.team_id AND rt.is_delete = 0 AND rt.deleted_at IS NULL
            LEFT JOIN team_member pm ON pm.team_id = p.team_id AND pm.user_id = #{actorUserId} AND pm.is_delete = 0 AND pm.deleted_at IS NULL
            LEFT JOIN team_member tm ON tm.team_id = r.team_id AND tm.user_id = #{actorUserId} AND tm.is_delete = 0 AND tm.deleted_at IS NULL
            WHERE r.is_delete = 0
              AND ((r.report_type = 'PROJECT_RISK' AND ((p.team_id IS NULL AND p.user_id = #{actorUserId})
                    OR (p.team_id IS NOT NULL AND pt.id IS NOT NULL AND (p.user_id = #{actorUserId} OR pm.id IS NOT NULL))))
                OR (r.report_type = 'TEAM_WORKLOAD' AND rt.id IS NOT NULL AND tm.id IS NOT NULL))
            <if test="reportType != null and reportType != ''">AND r.report_type = #{reportType}</if>
            <if test="projectId != null">AND r.project_id = #{projectId}</if>
            <if test="teamId != null">AND r.team_id = #{teamId}</if>
            ORDER BY r.create_time DESC, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiAnalysisReport> selectAccessiblePage(@Param("actorUserId") Long actorUserId,
                                                 @Param("reportType") String reportType,
                                                 @Param("projectId") Long projectId,
                                                 @Param("teamId") Long teamId,
                                                 @Param("offset") long offset,
                                                 @Param("limit") long limit);

    @Select("""
            <script>
            SELECT COUNT(DISTINCT r.id)
            FROM ai_analysis_report r
            LEFT JOIN project p ON p.id = r.project_id AND p.is_delete = 0 AND p.deleted_at IS NULL
            LEFT JOIN team pt ON pt.id = p.team_id AND pt.is_delete = 0 AND pt.deleted_at IS NULL
            LEFT JOIN team rt ON rt.id = r.team_id AND rt.is_delete = 0 AND rt.deleted_at IS NULL
            LEFT JOIN team_member pm ON pm.team_id = p.team_id AND pm.user_id = #{actorUserId} AND pm.is_delete = 0 AND pm.deleted_at IS NULL
            LEFT JOIN team_member tm ON tm.team_id = r.team_id AND tm.user_id = #{actorUserId} AND tm.is_delete = 0 AND tm.deleted_at IS NULL
            WHERE r.is_delete = 0
              AND ((r.report_type = 'PROJECT_RISK' AND ((p.team_id IS NULL AND p.user_id = #{actorUserId})
                    OR (p.team_id IS NOT NULL AND pt.id IS NOT NULL AND (p.user_id = #{actorUserId} OR pm.id IS NOT NULL))))
                OR (r.report_type = 'TEAM_WORKLOAD' AND rt.id IS NOT NULL AND tm.id IS NOT NULL))
            <if test="reportType != null and reportType != ''">AND r.report_type = #{reportType}</if>
            <if test="projectId != null">AND r.project_id = #{projectId}</if>
            <if test="teamId != null">AND r.team_id = #{teamId}</if>
            </script>
            """)
    long countAccessible(@Param("actorUserId") Long actorUserId,
                         @Param("reportType") String reportType,
                         @Param("projectId") Long projectId,
                         @Param("teamId") Long teamId);

    @Select("""
            SELECT id, report_id FROM ai_analysis_report
            WHERE is_delete=1 AND content_purged_at IS NULL AND deleted_at < #{cutoff}
              AND id > #{cursor} ORDER BY id LIMIT #{limit}
            """)
    List<AiAnalysisReport> selectDeletedForCleanup(@Param("cutoff") java.time.LocalDateTime cutoff,
                                                   @Param("cursor") long cursor,
                                                   @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM ai_analysis_report
            WHERE is_delete=1 AND content_purged_at IS NULL AND deleted_at < #{cutoff}
            """)
    long countDeletedForCleanup(@Param("cutoff") java.time.LocalDateTime cutoff);

    @Update("""
            <script>
            UPDATE ai_analysis_report
            SET manager_summary=NULL, public_summary=NULL, member_metrics_json=NULL,
                recommendations_json=NULL, content_purged_at=CURRENT_TIMESTAMP(3)
            WHERE is_delete=1 AND content_purged_at IS NULL AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int purgeDeletedContent(@Param("ids") List<Long> ids);
}
