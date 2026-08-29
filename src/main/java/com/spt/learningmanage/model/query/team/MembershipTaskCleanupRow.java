package com.spt.learningmanage.model.query.team;

import lombok.Data;

/**
 * Minimal locked task projection used by membership termination cleanup.
 * WP5-B deliberately keeps this projection free of display or private fields.
 */
@Data
public class MembershipTaskCleanupRow {

    private Long taskId;

    private Long assigneeUserId;
}
