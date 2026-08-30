package com.spt.learningmanage.service;

/**
 * Shared limits for permission-service resource batches.
 *
 * <p>Read paths may need to split a large history into several bounded
 * batches, but must never fall back to one permission query per resource.</p>
 */
public final class PermissionBatchPolicy {

    public static final int MAX_RESOURCE_IDS = 500;

    private PermissionBatchPolicy() {
    }
}
