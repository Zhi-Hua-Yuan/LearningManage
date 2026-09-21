# Phase 3 Review

## 已确认的安全边界

- SSE 只暴露 requestId、阶段名称、attempt 和最终 `RagAnswerVO`。
- 模型内容不会在最终 Citation 校验前到达客户端。
- 异步线程不读取请求线程的 `UserHolder`，而是使用显式 actorId。
- 客户端断开后不会保存部分回答；取消会将 Query Log 从 RUNNING 转为 CANCELED。
- Qdrant 仍是候选索引，MySQL 仍是业务事实源。

## 兼容性

- `/api/ai/rag/ask` 和 `/api/ai/rag/result/{requestId}` 不变。
- 无数据库迁移；`CANCELED` 是现有状态列允许的新审计终态。
- `AI_EMBEDDING_ADAPTER=legacy` 可回退旧 Embedding HTTP 实现。
- Chat 默认仍为 `legacy`，不提前执行 Phase 5 的 Adapter 下线。

## 未关闭风险

- 当前环境未完成真实 Qwen/Embedding 付费链路验证。
- 本地 MySQL 集成门禁依赖 `${TEST_DB_USERNAME}` 和 `${TEST_DB_PASSWORD}`。
- 生产代理对 SSE 的缓冲和超时需要 Docker/E2E 环境确认。
