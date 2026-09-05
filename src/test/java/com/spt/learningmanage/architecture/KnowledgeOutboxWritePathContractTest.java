package com.spt.learningmanage.architecture;

import com.spt.learningmanage.service.impl.KnowledgeIndexEventPublisherImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeOutboxWritePathContractTest {

    @Test
    void publisherRequiresAnExistingBusinessTransaction() throws Exception {
        for (Method method : KnowledgeIndexEventPublisherImpl.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("publish")) {
                continue;
            }
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertTrue(transactional != null, method + " must be transactional");
            assertEquals(Propagation.MANDATORY, transactional.propagation());
        }
    }

    @Test
    void frozenMutationOwnersAreConnectedToThePublisher() throws Exception {
        for (String path : List.of(
                "service/impl/TaskCreationServiceImpl.java",
                "service/impl/TaskAssignmentServiceImpl.java",
                "service/impl/TaskServiceImpl.java",
                "service/impl/WeeklyReviewServiceImpl.java",
                "service/impl/ProjectServiceImpl.java",
                "service/impl/TeamServiceImpl.java",
                "service/impl/TeamMembershipTerminationServiceImpl.java",
                "service/impl/ai/draft/WeeklyReviewPolishDraftHandler.java",
                "service/impl/ai/scene/ListReplanAiServiceImpl.java")) {
            String source = readMain(path);
            assertTrue(source.contains("KnowledgeIndexEventPublisher"),
                    path + " is missing durable knowledge-event publication");
        }
    }

    @Test
    void taskUpdateAndDeleteAreNowAtomicWithOutbox() throws Exception {
        String source = readMain("service/impl/TaskServiceImpl.java");
        assertTrue(source.matches("(?s).*@Transactional\\(rollbackFor = Exception.class\\)\\s+public void update.*"));
        assertTrue(source.matches("(?s).*@Transactional\\(rollbackFor = Exception.class\\)\\s+public void delete.*"));
    }

    private String readMain(String relative) throws Exception {
        Path file = Path.of("src/main/java/com/spt/learningmanage").resolve(relative);
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
