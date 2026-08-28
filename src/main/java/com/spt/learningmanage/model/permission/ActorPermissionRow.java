package com.spt.learningmanage.model.permission;

import lombok.Data;

/**
 * 当前 actor 的最小权限事实。
 *
 * <p>该类只承载用户身份生命周期和系统角色，不代表 actor 已经获得任何资源权限。</p>
 */
@Data
public class ActorPermissionRow {

    private Long actorUserId;
    private String actorSystemRole;
    private Integer actorIsDelete;
}
