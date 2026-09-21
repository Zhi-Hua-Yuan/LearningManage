# Phase 2 Handover

## 使用方式

五个场景会通过现有 `AiInvocationPipeline` 发送结构化响应契约。无需新增配置项；现有 `AI_CHAT_ADAPTER` 继续控制 Legacy/Spring AI Chat 传输适配器。默认适配器和既有 AI 开关不因本阶段改变。

## 兼容性

- REST path、请求/响应 VO、Draft payload、数据库 Schema 和 Flyway V1～V8 不变。
- `task-breakdown` 的模型响应从数组升级为对象包装，但服务层仍输出原来的 milestones 列表。
- 旧 Prompt 版本保留；新版本只通过 resolver 的最小兼容版本生效。

## 验证记录

- 解码器测试覆盖合法对象、代码围栏、非法 JSON、错误根类型、缺失字段和未知字段。
- 五个场景回归测试与 Prompt fixture 测试必须作为合并门禁。
- 真实 Qwen 状态：`WAIVED_NOT_RUN`。后续补跑必须记录模型、时间、provider request ID、风险和结果，不能写成通过。

## 回滚

代码级回滚到 Phase 1 合入基线即可；结构化迁移不产生数据库变更。若模型输出兼容性异常，可先切回 `AI_CHAT_ADAPTER=legacy`，再按既有 AI 功能开关逐项止损。
