# Phase 2：Structured Output 标准化需求

## 范围

- 在现有 `AiInvocationPipeline` 治理边界内，为五个 AI 场景接入 Spring AI `BeanOutputConverter`。
- 迁移顺序固定为：`today-order`、`daily-review-rename`、`weekly-polish`、`list-replan`、`task-breakdown`。
- Schema 解码严格区分 JSON Parse Failure 与 Schema Failure；不增加未经批准的自动重试。
- 结构合法后继续复用原有数量、日期、权限、资源状态、ID 合法性和 Draft 边界校验。
- `task-breakdown` 的模型内部响应改为 `{ "milestones": [...] }`，REST 响应和 Draft payload 保持兼容。
- 历史 Prompt 模板不覆盖；所有结构变化使用新版本（本阶段五个场景升级到 v2，task-breakdown 升级到 v3）。

## 非目标

- 不启用 provider-native Qwen Structured Output，待真实模型验证后另行决策。
- 不修改公共 REST API、数据库 Schema、Draft 确认流程或前端 wire contract。
- 不接入 RAG Streaming、ToolCallback、MCP、写 Tool 或新的通用 Chat API。

## 完成标准

- 五个场景均通过同一治理管线完成 typed decode，并保留既有业务校验和 fallback 行为。
- Parse、Schema、Business Validation 在调用记录和指标中可区分；AI 路由的授权失败和资源状态冲突有独立低基数指标。
- 结构化解码器覆盖合法 JSON、代码围栏、根类型错误、缺少字段、未知字段和非法 JSON。
- 非 MySQL 测试、场景回归和 Prompt fixture 校验通过；真实 Qwen 继续记录为 `WAIVED_NOT_RUN`。
