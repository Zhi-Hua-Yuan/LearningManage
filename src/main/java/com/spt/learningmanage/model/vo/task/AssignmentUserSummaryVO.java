package com.spt.learningmanage.model.vo.task;

import lombok.Data;

/**
 * 负责人历史中的最小用户展示摘要。
 *
 * <p>只允许返回稳定的公开标识和当前展示名；不承载账户、角色、删除状态
 * 等安全敏感字段。</p>
 */
@Data
public class AssignmentUserSummaryVO {

    private Long userId;

    private String username;
}
