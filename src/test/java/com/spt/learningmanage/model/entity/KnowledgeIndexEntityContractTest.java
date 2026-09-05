package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KnowledgeIndexEntityContractTest {

    @Test
    void entitiesMapToV4Tables() throws Exception {
        assertTable(AiKnowledgeIndexEvent.class, "ai_knowledge_index_event");
        assertTable(AiKnowledgeSourceLock.class, "ai_knowledge_source_lock");
        assertTable(AiKnowledgeDocument.class, "ai_knowledge_document");
        assertTable(AiKnowledgeBackfillRun.class, "ai_knowledge_backfill_run");

        assertIdType(AiKnowledgeIndexEvent.class, IdType.AUTO);
        assertIdType(AiKnowledgeDocument.class, IdType.ASSIGN_ID);
        assertIdType(AiKnowledgeBackfillRun.class, IdType.AUTO);
    }

    private void assertTable(Class<?> type, String expected) {
        TableName tableName = type.getAnnotation(TableName.class);
        assertNotNull(tableName);
        assertEquals(expected, tableName.value());
    }

    private void assertIdType(Class<?> type, IdType expected) throws Exception {
        Field id = type.getDeclaredField("id");
        assertEquals(expected, id.getAnnotation(TableId.class).type());
    }
}
