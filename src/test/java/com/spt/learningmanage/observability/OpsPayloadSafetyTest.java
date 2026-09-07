package com.spt.learningmanage.observability;

import com.spt.learningmanage.model.vo.ops.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsPayloadSafetyTest {
    private static final Set<String> FORBIDDEN = Set.of(
            "prompt", "question", "requesttext", "responsetext", "payloadjson",
            "argumentjson", "resultjson", "tasktitle", "reviewbody", "reportbody",
            "apikey", "password");

    @Test
    void operationsDtosCannotExposeAiOrBusinessBodies() {
        List<Class<?>> types = List.of(
                AiOpsOverviewVO.class, AiOpsSummaryVO.class, OpsFailureVO.class,
                DependencyStatusVO.class, CleanupRunVO.class, CleanupItemVO.class,
                CleanupCancelVO.class);
        for (Class<?> type : types) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                assertFalse(FORBIDDEN.stream().anyMatch(name::contains),
                        () -> type.getSimpleName() + " exposes forbidden field " + field.getName());
            }
        }
    }
}
