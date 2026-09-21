# Phase 5 Requirements：AI Eval、Spring AI 切换与 Legacy 下线

## 目标

在不改变业务 REST API、Draft 生命周期、权限模型和数据库事实源的前提下，完成 Spring AI Chat 的评测、默认切换和 Legacy 下线。

## 固定范围

- 核心 AI：5 个场景、6 个 Prompt code；复用 Stage 3 的 170 条质量样本和 40 条故障注入样本。
- RAG：复用 50 条已标注问题，并补充无答案、越权、陈旧版本负向覆盖。
- Agent：复用 Stage 6 的 100 条用例，并补充写 Tool 注入拒绝覆盖。
- Adapter：Stub 环境 Legacy/Spring AI 对等；真实模型只以 Spring AI 作为候选路径。
- Spring AI 不接管权限、限流、熔断、重试、审计、Outbox、Worker、Citation、Draft 或业务校验。

## 安全不变量

- 不注册全局 Tool，不启用 Spring AI 内部 Tool 执行。
- Tool 只能来自当前 Run 场景白名单，且重新鉴权、校验参数、限时和审计。
- Agent 只能生成 Draft，正式业务写入必须为零。
- RAG 回答必须通过最终权限、数据版本、hash 和 Citation 校验。
- Cleanup Worker 继续显式关闭。

## 发布顺序

1. 关闭 Phase 4 的真实 Provider、全栈和前端 E2E 待验证项。
2. 通过 Phase 5 双 Adapter 和 Spring AI 真实评测。
3. 将 Spring AI 设为默认，保留 `AI_CHAT_ADAPTER=legacy` 回退。
4. 稳定观察 7 天后删除 Legacy 和双轨配置。
