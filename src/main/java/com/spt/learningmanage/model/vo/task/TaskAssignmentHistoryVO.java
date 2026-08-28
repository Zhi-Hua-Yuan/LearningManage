package com.spt.learningmanage.model.vo.task;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务负责人变更历史对外视图。
 *
 * <p>fromAssignee/toAssignee 为 null 表示未分配；用户已删除或不可关联时，
 * 保留 userId 而将 username 置为 null，以保留审计可追溯性。</p>
 */
@Data
public class TaskAssignmentHistoryVO {

    private Long id;

    private Long taskId;

    private String action;

    private AssignmentUserSummaryVO fromAssignee;

    private AssignmentUserSummaryVO toAssignee;

    private AssignmentUserSummaryVO assignedBy;

    private String reason;

    private LocalDateTime createTime;
}
