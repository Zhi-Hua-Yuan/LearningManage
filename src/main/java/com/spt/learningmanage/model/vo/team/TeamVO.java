package com.spt.learningmanage.model.vo.team;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 团队信息VO
 */
@Data
public class TeamVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 团队ID
     */
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
     * 当前用户在团队中的角色
     */
    private String role;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
