# Phase 4 Requirements：Agent / Tool Calling 迁移

## 目标

- 将 6 个现有只读 Agent Tool 的定义统一收敛到 `LearningManageToolCallingManager`。
- 使用 Spring AI `ToolDefinition` / 请求级 `ToolCallback` 描述 Tool，但不启用框架内部执行。
- 保留 Legacy/Spring AI Adapter 对等行为、Agent Worker、Draft 确认、数据版本和 Citation 安全边界。

## Tool 边界

| 场景 | Tool |
| --- | --- |
| PROJECT_RISK | `queryProjectTasks`、`queryOverdueTasks`、`queryTaskStats`、`retrieveProjectHistory` |
| TEAM_WORKLOAD | `queryTeamMemberWorkload`、`queryMemberOverdueTasks` |

Tool 参数只允许业务查询参数。`actorId`、`projectId`、`teamId`、`runId` 和 `executionToken` 始终来自持久化 Run/ExecutionContext。

RAG 关闭时，`retrieveProjectHistory` 不进入本次 Run 的定义集合。团队负载继续走固定只读工作流，不开放模型自主选 Tool。

## 执行与失败策略

- 每个 Run 只注入当前场景的白名单 Tool。
- 每次执行重新进行场景策略、Bean Validation、权限检查、超时、输出长度和审计校验。
- 同一执行 attempt 内同一 Tool 不得重复调用，最多调用 4 次。
- Tool Calling 失败降级到固定只读流程；降级结果只能是 `PARTIAL`，不得伪装成完整成功。
- 不注册全局 Tool，不创建写 Tool，不启用 Spring AI 内部 Tool 执行。

## 兼容性

- 不新增 REST API、Flyway 迁移或公共 VO。
- Legacy Adapter 继续接收既有 `AiToolDefinition`；Spring AI Adapter 以请求级 Callback 生成等价 OpenAI-compatible tools。
- `AI_AGENT_TOOL_CALLING_ENABLED=false`、`AI_CHAT_ADAPTER=legacy` 仍可用于回退。
