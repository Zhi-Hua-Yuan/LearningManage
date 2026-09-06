package com.spt.learningmanage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.ai.governance.DefaultAiContentSanitizer;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.config.RerankProperties;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.model.dto.rag.RerankCandidate;
import com.spt.learningmanage.model.dto.rag.RerankRequest;
import com.spt.learningmanage.service.impl.AliyunEmbeddingClient;
import com.spt.learningmanage.service.impl.AliyunRerankClient;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import com.spt.learningmanage.service.rag.RagResilientCallExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "STAGE5_REAL_RAG_DEPENDENCIES_ENABLED", matches = "true")
class RealRagDependencyValidationIT {

    @Test
    void validatesRealQueryEmbeddingModeAndDimension() {
        ObjectMapper mapper = new ObjectMapper();
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl(required("AI_EMBEDDING_BASE_URL"));
        properties.setQueryBaseUrl(required("AI_EMBEDDING_QUERY_BASE_URL"));
        properties.setApiKey(required("ALIYUN_API_KEY"));
        properties.setModel("text-embedding-v4");
        properties.setDimension(1024);
        AliyunEmbeddingClient client = new AliyunEmbeddingClient(
                properties, mapper, new DefaultAiContentSanitizer(mapper, new AiProperties()),
                new KnowledgeResilientCallExecutor(new KnowledgeIndexProperties()));

        var result = client.embedQuery("哪个任务负责验证权限感知检索？",
                new EmbeddingCallContext(1L, "stage5-real-query-embedding", List.of("synthetic")));

        assertEquals(1, result.vectors().size());
        assertEquals(1024, result.vectors().get(0).size());
        assertEquals("text-embedding-v4", result.actualModel());
        assertNotNull(result.totalTokens());
        assertTrue(result.totalTokens() > 0);
    }

    @Test
    void validatesRealQwen3RerankOrderingAndMetadata() {
        ObjectMapper mapper = new ObjectMapper();
        RerankProperties properties = new RerankProperties();
        properties.setBaseUrl(required("AI_RERANK_BASE_URL"));
        properties.setApiKey(required("ALIYUN_API_KEY"));
        properties.setModel("qwen3-rerank");
        AliyunRerankClient client = new AliyunRerankClient(
                properties, mapper, new DefaultAiContentSanitizer(mapper, new AiProperties()),
                new RagResilientCallExecutor(properties));

        var result = client.rerank(new RerankRequest(
                "哪个任务负责权限感知的项目检索？",
                List.of(
                        new RerankCandidate("relevant", "实现项目级权限过滤、二次鉴权和引用校验"),
                        new RerankCandidate("irrelevant", "整理英语单词并完成听力练习"),
                        new RerankCandidate("partial", "配置项目页面的颜色和图标")
                ), 3, "stage5-real-rerank"));

        assertFalse(result.items().isEmpty());
        assertEquals("relevant", result.items().get(0).candidateId());
        assertEquals("qwen3-rerank", result.actualModel());
        assertNotNull(result.providerRequestId());
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing protected environment: " + name);
        }
        return value.trim();
    }
}
