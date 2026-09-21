# Phase 4 Review

## 已落地边界

- Tool 定义由注册表中的 AgentTool 元数据生成，编排器不再手写项目风险 Tool Schema。
- Spring AI Callback 是请求级定义载体；其 `call()` fail-closed，业务执行只允许进入应用级 Manager。
- Manager 统一调用 `AgentToolPolicy`、`AgentToolExecutor` 和 `AgentRunQueueService`，因此权限、超时、审计、进度和 execution token 仍在原安全内核内。
- 团队负载仍是固定工作流，未扩大为模型自主 Tool Calling。

## 待验证风险

- 需要在受保护环境完成真实 Qwen Tool Calling 及多轮 Tool Call 验证。
- 需要在代理、Redis、Qdrant 和隔离 MySQL 环境确认默认开启矩阵。
- 当前编译仍有既有 Spring Framework/Lombok 弃用警告，不属于本阶段功能失败。
