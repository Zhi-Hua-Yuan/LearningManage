package com.spt.learningmanage.agent;

import jakarta.validation.constraints.Size;

public record ProjectHistoryArguments(@Size(max = 200) String query) {
}
