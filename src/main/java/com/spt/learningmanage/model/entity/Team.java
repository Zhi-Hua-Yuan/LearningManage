package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 团队实体
 */
@Data
@TableName("team")
public class Team implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 团队ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 团队名称
     */
    private String name;

    /**
     * 团队描述
     */
    private String description;

    /**
     * 团队创建者用户ID
     */
    private Long ownerId;

    /**
     * 团队邀请码
     */
    private String inviteCode;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer isDelete;
}
