package com.spt.learningmanage.model.vo.team;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建团队返回VO
 */
@Data
public class TeamCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 团队邀请码
     */
    private String inviteCode;
}
