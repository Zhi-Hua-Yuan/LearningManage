package com.spt.learningmanage.model.dto.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CleanupRunCreateRequest {
    private Boolean dryRun = true;

    @Size(max = 9, message = "resourceTypes 数量不能超过 9")
    private List<String> resourceTypes;

    @Pattern(regexp = "cleanup_[A-Za-z0-9]{16,56}", message = "approvedDryRunId 格式不合法")
    private String approvedDryRunId;

    @NotBlank(message = "clientRequestId 不能为空")
    @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}", message = "clientRequestId 格式不合法")
    private String clientRequestId;
}
