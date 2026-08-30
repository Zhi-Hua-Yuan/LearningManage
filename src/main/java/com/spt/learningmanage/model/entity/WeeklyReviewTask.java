package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("weekly_review_task")
public class WeeklyReviewTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long weeklyReviewId;
    private Long taskId;
    private LocalDateTime createTime;
}
