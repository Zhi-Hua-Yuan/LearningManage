package com.spt.learningmanage.model.ops;

public record CleanupBatchResult(
        long scanned,
        long affected,
        long redacted,
        long deleted,
        long nextCursor,
        boolean finished
) {
}
