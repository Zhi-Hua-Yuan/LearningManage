package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.KnowledgeVisibilityTypeEnum;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.KnowledgeDocumentFactory;
import com.spt.learningmanage.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = LearningManageApplication.class, properties = {
        "app.scheduling.enabled=false",
        "ai.rag.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RagCandidateHydratorCorrectionMySqlTest {
    private static final long SOURCE_ID = 2_096_900_000_000_060L;
    private static final long ACTOR_ID = 2_096_900_000_000_061L;
    private static final long PROJECT_ID = 2_096_900_000_000_062L;

    @Autowired RagCandidateHydrator hydrator;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockBean KnowledgeDocumentFactory documentFactory;
    @MockBean PermissionService permissionService;

    @BeforeEach
    void setUp() {
        assertIsolatedDatabase();
        cleanup();
        Mockito.reset(documentFactory, permissionService);
    }

    @AfterEach
    void tearDown() {
        cleanup();
        Mockito.reset(documentFactory, permissionService);
    }

    @Test
    void staleCandidatesCreateOneCommittedCorrectionWithoutOuterTransaction() {
        KnowledgeSourceRef source = new KnowledgeSourceRef(KnowledgeSourceTypeEnum.TASK, SOURCE_ID);
        when(documentFactory.buildDesiredDocuments(source)).thenReturn(List.of(projection()));
        when(permissionService.filterReadableTaskIds(ACTOR_ID, List.of(SOURCE_ID)))
                .thenReturn(Set.of(SOURCE_ID));
        when(permissionService.filterReadableWeeklyReviewIds(ACTOR_ID, List.of()))
                .thenReturn(Set.of());

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "the hydration caller must not provide a transaction");
        var candidates = hydrator.hydrate(ACTOR_ID, scope(), List.of(
                new VectorSearchHit("stale-point-1", 0.9, stalePayload()),
                new VectorSearchHit("stale-point-2", 0.8, stalePayload())));

        assertEquals(List.of(), candidates);
        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
                SELECT source_type,source_id,event_type,status,attempt_count
                FROM ai_knowledge_index_event
                WHERE source_type='TASK' AND source_id=?
                """, SOURCE_ID);
        assertEquals(1, events.size(), "one hydration must deduplicate correction by source");
        Map<String, Object> event = events.get(0);
        assertEquals("TASK", event.get("source_type"));
        assertEquals(SOURCE_ID, ((Number) event.get("source_id")).longValue());
        assertEquals("SOURCE_CHANGED", event.get("event_type"));
        assertEquals("PENDING", event.get("status"));
        assertEquals(0, ((Number) event.get("attempt_count")).intValue());
    }

    private void assertIsolatedDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"),
                "correction integration test must use an isolated test database");
        Integer version = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1",
                Integer.class);
        assertNotNull(version);
        assertTrue(version >= 4, "knowledge outbox schema must be available");
    }

    private ProjectAccessScope scope() {
        return new ProjectAccessScope(ACTOR_ID, PROJECT_ID, ACTOR_ID, null, null);
    }

    private KnowledgeDocumentProjection projection() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", "TASK");
        payload.put("sourceId", SOURCE_ID);
        payload.put("projectId", PROJECT_ID);
        payload.put("userId", ACTOR_ID);
        payload.put("ownerUserId", ACTOR_ID);
        payload.put("visibilityType", "PRIVATE");
        return new KnowledgeDocumentProjection(
                documentKey(), KnowledgeSourceTypeEnum.TASK, SOURCE_ID, PROJECT_ID,
                null, ACTOR_ID, KnowledgeVisibilityTypeEnum.PRIVATE,
                "任务标题: correction", "synthetic current content", payload);
    }

    private Map<String, Object> stalePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", "TASK");
        payload.put("sourceId", SOURCE_ID);
        payload.put("projectId", PROJECT_ID);
        payload.put("ownerUserId", ACTOR_ID);
        payload.put("visibilityType", "PRIVATE");
        payload.put("documentKey", documentKey());
        payload.put("chunkIndex", 0);
        payload.put("sourceVersion", "stale:source-version");
        return payload;
    }

    private String documentKey() {
        return "TASK:" + SOURCE_ID + ":PRIVATE:" + PROJECT_ID;
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_knowledge_index_event WHERE source_type='TASK' AND source_id=?",
                SOURCE_ID);
    }
}
