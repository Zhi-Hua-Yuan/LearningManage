package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.Milestone;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MilestoneMapper extends BaseMapper<Milestone> {

    @Select("SELECT COALESCE(MAX(order_no), 0) FROM milestone WHERE project_id = #{projectId}")
    Integer selectMaxOrderNoIncludingDeleted(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM milestone WHERE project_id = #{projectId} " +
            "AND order_no = #{orderNo} AND id <> #{milestoneId}")
    long countOrderNoIncludingDeletedExcludingId(@Param("projectId") Long projectId,
                                                  @Param("orderNo") Integer orderNo,
                                                  @Param("milestoneId") Long milestoneId);

	@Update("""
			UPDATE milestone
			SET is_delete = 0,
			    deleted_at = NULL,
			    delete_source = 0
			WHERE project_id = #{projectId}
			  AND is_delete = 1
			  AND delete_source = 2
			""")
	int recoverByProjectId(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
