package com.spt.learningmanage.model.dto.task;

import lombok.Data;

/**
 * 任务负责人历史查询参数。
 *
 * <p>分页字段使用 API 契约中的 current/size 命名；边界校验和权限校验由
 * WP4-D2 的 service 层统一处理。</p>
 */
@Data
public class TaskAssignmentHistoryQueryRequest {

    private Long taskId;

    private Long current = 1L;

    private Long size = 50L;
}
