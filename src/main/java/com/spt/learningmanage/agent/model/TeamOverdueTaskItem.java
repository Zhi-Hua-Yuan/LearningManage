package com.spt.learningmanage.agent.model;

import java.time.LocalDate;

public record TeamOverdueTaskItem(String localId,
                                  String memberAlias,
                                  String title,
                                  Integer priority,
                                  LocalDate dueDate) {
}

