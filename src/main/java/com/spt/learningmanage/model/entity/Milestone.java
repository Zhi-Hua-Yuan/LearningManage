package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("milestone")
public class Milestone {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;
    private Long userId;
    private String name;
    private Integer orderNo;
    private BigDecimal progress;

    @TableLogic
    private Integer isDelete;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
