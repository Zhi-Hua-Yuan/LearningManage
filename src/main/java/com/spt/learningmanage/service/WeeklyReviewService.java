package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.review.WeeklyReviewSaveRequest;
import com.spt.learningmanage.model.dto.review.WeeklyReviewTeamQueryRequest;
import com.spt.learningmanage.model.dto.review.WeeklyReviewUpdateRequest;
import com.spt.learningmanage.model.vo.review.WeeklyReviewDetailVO;
import com.spt.learningmanage.model.vo.review.WeeklyReviewSharedVO;

import java.util.List;

public interface WeeklyReviewService {

    /**
     * 获取或生成当前周的周总结草稿。
     */
    WeeklyReviewDetailVO getCurrentWeekReview();

    /**
     * 保存或更新周总结。
     */
    void saveReview(WeeklyReviewSaveRequest request);

    /**
     * 根据ID获取周总结详情
     * @param id 周总结ID
     * @return 周总结详细信息
     */
    WeeklyReviewDetailVO getReviewById(Long id);

    /**
     * 更新周总结
     */
    void updateReview(WeeklyReviewUpdateRequest request);

    /**
     * 删除周总结
     */
    void deleteReview(Long id);

    /**
     * 获取历史周总结列表。
     */
    List<WeeklyReviewDetailVO> listHistory();

    Page<WeeklyReviewSharedVO> listTeamSharedReviews(WeeklyReviewTeamQueryRequest request);
}

