# Phase 4 Handover

## 配置与回退

默认配置保持：

```yaml
ai:
  agent:
    enabled: ${AI_AGENT_ENABLED:true}
    worker-enabled: ${AI_AGENT_WORKER_ENABLED:true}
    tool-calling-enabled: ${AI_AGENT_TOOL_CALLING_ENABLED:true}
  chat:
    adapter: ${AI_CHAT_ADAPTER:legacy}
```

紧急回退顺序：

1. `AI_AGENT_TOOL_CALLING_ENABLED=false`
2. `AI_AGENT_WORKER_ENABLED=false`
3. `AI_AGENT_ENABLED=false`
4. 必要时 `AI_CHAT_ADAPTER=legacy`

## 执行约束

Spring AI ChatModel 不得配置全局 Tool Callback，且每次请求必须设置 `internalToolExecutionEnabled(false)`。Callback 只用于发送定义；模型返回的调用由 `LearningManageToolCallingManager` 使用持久化 Run 上下文执行。

## 验证状态

- 编译通过。
- Manager 定向测试通过。
- 原有 Agent/Spring AI 定向回归通过。
- 真实 Provider、隔离依赖和 Docker 验证待在受保护环境执行。
