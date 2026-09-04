package com.spt.learningmanage.service.impl.ai.draft;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.model.dto.ai.draft.TaskBreakdownConfirmationContext;
import com.spt.learningmanage.service.ai.draft.AiDraftHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAiDraftHandlerRegistryTest {

    @Test
    void rejectsDuplicateSceneAtStartup() {
        AiDraftHandler<?> first = handler("task-breakdown");
        AiDraftHandler<?> second = handler("task-breakdown");

        assertThrows(IllegalStateException.class,
                () -> new DefaultAiDraftHandlerRegistry(List.of(first, second)));
    }

    @Test
    void returnsCurrentVersionAndValidatesContextType() {
        AiDraftHandler<?> handler = handler("task-breakdown");
        DefaultAiDraftHandlerRegistry registry = new DefaultAiDraftHandlerRegistry(List.of(handler));

        assertEquals(1, registry.currentSchemaVersion("task-breakdown"));
        assertEquals(handler, registry.require("task-breakdown",
                new TaskBreakdownConfirmationContext(null, null)));
    }

    @Test
    void rejectsUnknownScene() {
        DefaultAiDraftHandlerRegistry registry = new DefaultAiDraftHandlerRegistry(List.of());

        assertThrows(BusinessException.class,
                () -> registry.currentSchemaVersion("unknown"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private AiDraftHandler<?> handler(String scene) {
        AiDraftHandler handler = mock(AiDraftHandler.class);
        when(handler.scene()).thenReturn(scene);
        when(handler.currentSchemaVersion()).thenReturn(1);
        when(handler.supportedSchemaVersions()).thenReturn(Set.of(1));
        when(handler.contextType()).thenReturn(TaskBreakdownConfirmationContext.class);
        return handler;
    }
}
