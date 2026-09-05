package com.spt.learningmanage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.ai.governance.DefaultAiContentSanitizer;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.service.impl.AliyunEmbeddingClient;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "STAGE4_REAL_EMBEDDING_ENABLED", matches = "true")
class RealEmbeddingValidationIT {

    @Test
    void validatesTenSyntheticDocumentsWithoutPersistingBodiesOrVectors() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl(required("AI_EMBEDDING_BASE_URL"));
        properties.setApiKey(required("ALIYUN_API_KEY"));
        properties.setModel(environment("AI_EMBEDDING_MODEL", "text-embedding-v4"));
        properties.setDimension(1024);
        properties.setMaxBatchSize(10);
        ObjectMapper mapper = new ObjectMapper();
        AiProperties aiProperties = new AiProperties();
        AliyunEmbeddingClient client = new AliyunEmbeddingClient(
                properties, mapper, new DefaultAiContentSanitizer(mapper, aiProperties),
                new KnowledgeResilientCallExecutor(new KnowledgeIndexProperties()));
        List<String> documents = List.of(
                "设计项目验收标准", "实现任务状态机", "补充权限矩阵测试", "记录周复盘共享摘要",
                "验证事务消息一致性", "实现向量索引回填", "处理外部服务超时", "建立失败事件重放",
                "验证确定性向量标识", "整理阶段交付证据"
        );
        KnowledgeHashing hashing = new KnowledgeHashing(mapper);
        var result = client.embedDocuments(documents, new EmbeddingCallContext(
                1L, "stage4-real-embedding-smoke",
                documents.stream().map(hashing::sha256).toList()));

        assertEquals(10, result.vectors().size());
        assertTrue(result.vectors().stream().allMatch(vector -> vector.size() == 1024));
        assertNotNull(result.actualModel());
        assertNotNull(result.promptTokens());
        assertTrue(result.promptTokens() > 0);
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing protected environment: " + name);
        }
        return value.trim();
    }

    private String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
