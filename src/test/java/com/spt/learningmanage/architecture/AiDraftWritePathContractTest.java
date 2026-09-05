package com.spt.learningmanage.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDraftWritePathContractTest {

    @Test
    void sceneServicesDelegateConfirmationWithoutWritingDraftTerminalState() throws Exception {
        String taskScene = source("service/impl/ai/scene/TaskBreakdownAiServiceImpl.java");
        String weeklyScene = source("service/impl/ai/scene/WeeklyReviewAiServiceImpl.java");

        assertTrue(taskScene.contains("draftConfirmationService.confirm"));
        assertTrue(weeklyScene.contains("draftConfirmationService.confirm"));
        assertFalse(taskScene.contains("AiDraftConfirmLogMapper"));
        assertFalse(weeklyScene.contains("AiDraftConfirmLogMapper"));
        assertFalse(taskScene.contains("markConfirmed("));
        assertFalse(weeklyScene.contains("markConfirmed("));
    }

    @Test
    void onlyConfirmationKernelOwnsGenericConfirmationLogInsert() throws Exception {
        String kernel = source("service/impl/ai/draft/AiDraftConfirmationServiceImpl.java");
        assertTrue(kernel.contains("confirmLogMapper.insert"));

        Path root = Path.of("src/main/java/com/spt/learningmanage");
        try (var paths = Files.walk(root)) {
            long otherWriters = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().endsWith("AiDraftConfirmationServiceImpl.java"))
                    .map(this::readUnchecked)
                    .filter(content -> content.contains("AiDraftConfirmLogMapper"))
                    .filter(content -> content.contains(".insert("))
                    .count();
            assertTrue(otherWriters == 0, "确认日志只能由统一确认内核写入");
        }
    }

    @Test
    void stage2DraftSafetyRemainsIntactAfterStage4Migration() throws Exception {
        String v4 = Files.readString(Path.of(
                "src/main/resources/db/migration/V4__stage4_knowledge_index_and_outbox.sql"));
        assertTrue(v4.contains("CREATE TABLE `ai_knowledge_index_event`"));
        assertFalse(v4.matches("(?is).*ALTER\\s+TABLE\\s+`?ai_draft`?.*"));
        assertFalse(v4.matches("(?is).*ALTER\\s+TABLE\\s+`?ai_draft_confirm_log`?.*"));
        String taskMapper = source("mapper/TaskMapper.java");
        String replanScene = source("service/impl/ai/scene/ListReplanAiServiceImpl.java");
        assertTrue(taskMapper.contains("compareAndSetReplan"));
        assertTrue(taskMapper.contains("update_time <=> #{expectedUpdateTime}"));
        assertTrue(replanScene.contains("requireAllTasksReorganizable"));
        assertTrue(replanScene.contains("taskMapper.compareAndSetReplan"));
    }

    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/spt/learningmanage").resolve(relative));
    }

    private String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
