package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String goal;
    private Integer status;
    private LocalDate startDate;
    private LocalDate endDate;
    @TableLogic
    private Integer isDelete;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime deletedAt;
}
