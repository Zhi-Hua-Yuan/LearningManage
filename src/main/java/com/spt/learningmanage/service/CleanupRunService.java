package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.ops.CleanupRunCreateRequest;
import com.spt.learningmanage.model.vo.ops.CleanupCancelVO;
import com.spt.learningmanage.model.vo.ops.CleanupRunVO;

public interface CleanupRunService {
    CleanupRunVO submit(CleanupRunCreateRequest request);

    CleanupRunVO get(String runId);

    Page<CleanupRunVO> list(long current, long size);

    CleanupCancelVO cancel(String runId);

    void submitScheduled();

    void submitScheduledFormal(String approvedDryRunId);
}
