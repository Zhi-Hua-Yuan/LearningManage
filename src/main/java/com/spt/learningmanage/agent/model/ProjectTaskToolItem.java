package com.spt.learningmanage.agent.model;

import java.time.LocalDate;

public record ProjectTaskToolItem(
        String localId,
        String title,
        Integer status,
        Integer priority,
        LocalDate dueDate,
        boolean assigned
) {
}

