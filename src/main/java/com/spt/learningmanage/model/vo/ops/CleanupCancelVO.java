package com.spt.learningmanage.model.vo.ops;

public record CleanupCancelVO(String runId, String status, boolean cancellationRequested) {
}
