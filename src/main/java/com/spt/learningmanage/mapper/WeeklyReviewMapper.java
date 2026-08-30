package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.vo.review.WeeklyReviewSharedVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WeeklyReviewMapper extends BaseMapper<WeeklyReview> {

    WeeklyReview selectByIdForUpdate(@Param("id") Long id);

    WeeklyReview selectByUserYearWeekForUpdate(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("weekNo") Integer weekNo
    );

    /** Explicit write update; nullable scope/association fields must be cleared. */
    int updateForWrite(WeeklyReview review);

    Page<WeeklyReviewSharedVO> selectTeamSharedPage(
            Page<WeeklyReviewSharedVO> page,
            @Param("teamId") Long teamId
    );
}

