package com.spt.learningmanage.service;

import com.spt.learningmanage.constant.CleanupResourceTypeEnum;

import java.time.LocalDateTime;

public interface DataRetentionPolicy {
    String version();

    LocalDateTime cutoff(CleanupResourceTypeEnum resourceType, LocalDateTime now);
}
