# Phase 4 TODO

## 后端

- [x] 为 AgentTool 增加稳定描述元数据。
- [x] 通过参数类型生成稳定 Tool Schema。
- [x] 实现 `LearningManageToolCallingManager`。
- [x] 将项目风险 Tool Calling 和固定工作流统一接入 Manager。
- [x] Spring AI Adapter 改为请求级 `ToolCallback`，并保持内部执行关闭。
- [x] 保留 Tool Policy、Executor、权限重验、超时、审计和 Worker fencing。
- [ ] 增加受保护真实 Qwen Tool Calling 验证报告。

## 测试与门禁

- [x] Manager 白名单、RAG 过滤、重复调用和上下文隔离测试。
- [x] Spring AI/Legacy Adapter 对等测试。
- [x] Agent 安全架构测试和现有 Worker/Service 回归。
- [ ] Spring Adapter 生产配置启动验证。
- [ ] 隔离 MySQL、Redis、Qdrant、Docker 全栈门禁。
- [ ] 前端全量兼容回归和 E2E。
