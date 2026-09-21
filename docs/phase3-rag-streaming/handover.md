# Phase 3 Handover

## 配置

```yaml
ai:
  embedding:
    adapter: ${AI_EMBEDDING_ADAPTER:spring-ai}
  rag:
    stream-timeout-ms: ${AI_RAG_STREAM_TIMEOUT_MS:120000}
    stream-worker-concurrency: ${AI_RAG_STREAM_WORKER_CONCURRENCY:4}
    stream-queue-capacity: ${AI_RAG_STREAM_QUEUE_CAPACITY:16}
```

紧急回退：

```bash
AI_EMBEDDING_ADAPTER=legacy
AI_RAG_ENABLED=false
```

## API

- 同步问答：`POST /api/ai/rag/ask`。
- 流式问答：`POST /api/ai/rag/ask/stream`，事件为 `accepted`、`stage`、`complete`、`error`。
- 结果读取：`GET /api/ai/rag/result/{requestId}`。

前端流式调用使用 POST `fetch`，不能改用原生 EventSource，因为请求需要 JSON body 和 Authorization Header。

## 回滚

1. 设置 `AI_EMBEDDING_ADAPTER=legacy` 并重建 Backend。
2. 若 RAG 依赖异常，设置 `AI_RAG_ENABLED=false`。
3. 同步 RAG 接口和核心项目/任务/团队业务继续运行。

真实模型状态必须单独记录模型、时间、provider request ID、预算和结果；未运行项不得标记为通过。
