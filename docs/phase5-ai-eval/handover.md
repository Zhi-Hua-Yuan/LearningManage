# Phase 5 Handover

## 当前回退方式

在 Legacy 尚未删除的候选版本中设置：

```bash
AI_CHAT_ADAPTER=legacy
```

AI 功能仍可按既有顺序显式关闭：Tool Calling、Agent Worker、Agent、RAG、Knowledge Worker。核心项目、任务、团队和周复盘业务不得受影响。

## 最终回退方式

Legacy 删除后，优先部署观察期冻结的 `stage9-spring-ai-cutover-rc1` 制品并启用 Legacy；不执行 Flyway downgrade。灾难级回退使用 `stage8-pre-spring-ai-v1.0.0`，Qdrant 通过 Outbox replay/rebuild 恢复。
