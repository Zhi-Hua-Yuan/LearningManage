package com.spt.learningmanage.model.vo.ops;

import java.time.LocalDateTime;

public record DependencyStatusVO(
        String name,
        String status,
        String detail,
        LocalDateTime checkedAt
) {
}
