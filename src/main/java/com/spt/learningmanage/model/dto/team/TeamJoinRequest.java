package com.spt.learningmanage.model.dto.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 加入团队请求
 */
@Data
public class TeamJoinRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 团队邀请码
     */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
