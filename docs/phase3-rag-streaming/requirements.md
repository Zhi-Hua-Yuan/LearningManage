# Phase 3 Requirements：RAG 适配与安全 Streaming

## 目标

- 在不改变 Qdrant REST、权限 Payload、Outbox、Knowledge Worker、Citation 和 MySQL 事实源边界的前提下，引入 Spring AI `EmbeddingModel` 抽象。
- 新增权限感知 RAG SSE 接口，只发送已授权的阶段事件和最终完整答案。
- 原同步 RAG 接口、结果读取接口、`RagAnswerVO` 和已发布 V1-V8 数据库结构保持兼容；取消审计状态通过 forward-only V9 扩展。

## Embedding

- `ai.embedding.adapter` 只允许 `spring-ai` 和 `legacy`。
- `spring-ai` 默认启用；`legacy` 用于紧急回退。
- 文档 Embedding 继续使用 OpenAI-compatible 协议；Query Embedding 继续使用 Qwen Query 协议。
- 保留内容脱敏、维度校验、Usage、实际模型、provider request ID、限流、熔断和并发隔离。
- 禁止 Spring AI 自动创建未治理的 Embedding 或 Qdrant VectorStore Bean。

## Streaming

新增：

```http
POST /api/ai/rag/ask/stream
Accept: text/event-stream
```

允许的事件：`accepted`、`stage`、`complete`、`error`。`stage` 只允许 `RETRIEVING`、`RERANKING`、`GENERATING`、`VERIFYING`。

- 不发送原始 token delta、证据正文、业务 ID 或 provider 错误原文。
- `complete` 只能在最终权限、Citation、hash/version 校验完成后发送。
- 客户端断开、超时或取消时不持久化不完整结果。
- `AI_CHAT_ADAPTER=legacy` 使用现有同步生成完成阶段流；`spring-ai` 保留内部可取消 Streaming seam。

## 非目标

- 不迁移到 Spring AI Qdrant VectorStore。
- 不新增写 Tool、Agent、Memory、MCP 或聊天历史。
- 不修改已发布 Flyway V1-V8、同步 RAG API、RAG 结果 VO 或前端 Citation 数据结构；仅新增 V9 取消状态约束迁移。
