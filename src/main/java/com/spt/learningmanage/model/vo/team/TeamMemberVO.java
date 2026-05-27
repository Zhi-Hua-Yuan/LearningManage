package com.spt.learningmanage.model.vo.team;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 团队成员VO
 */
@Data
public class TeamMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 成员角色
     */
    private String role;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;
}
