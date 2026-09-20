# Phase 1 PR2 Handover

## 使用方式

```text
AI_CHAT_ADAPTER=legacy     # 默认，生产回滚基线
AI_CHAT_ADAPTER=spring-ai  # 确定性 Stub/受控验证时启用
```

配置只接受 `legacy` 和 `spring-ai`；其他值必须在启动阶段失败。生产默认仍为 `legacy`，代码级回滚基线为 `stage8-pre-spring-ai-v1.0.0`。

## 验证记录要求

合入前保存非 MySQL Maven verification、隔离 MySQL/并发/Flyway、依赖和边界门禁、Docker 全栈、API/OpenAPI、前端和双仓 Release Gate 的链接、精确 SHA 与测试总数。真实 Qwen 项目固定记录：`WAIVED_NOT_RUN`，并写明原因、风险和后续补跑要求。

## Phase 2 入口

仅在 PR 合入、两套 Adapter 确定性对等验证通过、全门禁通过且豁免被准确记录后进入 Phase 2；不得在本阶段提前迁移 Structured Output、RAG Streaming 或 Agent ToolCallback。
