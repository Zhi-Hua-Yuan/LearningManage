# Phase 1 PR2：Spring AI Chat 双轨接入需求

## 范围

- `legacy` 继续作为默认 Chat Adapter；通过 `AI_CHAT_ADAPTER=spring-ai` 显式切换。
- `AiModelClientImpl` 保持唯一治理入口，Adapter 只负责传输映射、单次调用和错误分类。
- Spring AI 仅用于 Chat 传输，不接管治理、RAG、Memory、Tool 执行或业务校验。
- 新增内部 Streaming 契约，但 Phase 1 不暴露 Controller、不接入 RAG/Agent/前端。
- REST API、Prompt、Draft payload、数据库 Schema 和 V1～V8 migration 保持不变。

## 完成标准

两套 Adapter 使用同一确定性 Stub 覆盖普通文本、强制 Tool Call、Tool Result 回传、五个业务场景和六个 Prompt code；标准化比较 content、tool calls、finish reason、Usage 和实际模型。两套请求 ID 均须非空并进入审计，但允许具体值不同。

真实 Qwen 不属于本阶段执行范围，验收状态固定为 `WAIVED_NOT_RUN`。

## 合并后收尾证据（2026-09-20）

- PR：[#170](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/170)，已于 `2026-09-20T11:18:43Z` 合入 `develop`。
- 后端 `develop` merge SHA：`42fe52ed803f8ebe465e3d1b2809ef38df20dc0f`。
- 前端配对 `develop` SHA：`5561e8bdbfbfbc96b78f75dc6d8de4015f5ee430`。
- 跨仓精确 SHA Release Gate： [run 35507744883](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/35507744883)，候选 `phase1-postmerge-20260920-001`，结果 `PASS`。
- Gate 覆盖并通过：后端 959 测试与制品、前端测试/静态检查/构建、空库与存量库 Flyway、Docker 全栈、运行时 OpenAPI breaking-change、AI breakdown 生命周期和候选 manifest。
- 候选 manifest SHA-256：`0EDE3F7AD6DA9D1F93BEE69EC7D0ADF1A3212673396E70586C379E8870D88AFA`。
- Stage 0 绑定状态仍以候选 manifest 为准：`BOUND`、`PROVISIONAL`、`pendingClosingGateCount=2`；这不改变本 Phase 1 PR2 的实现结果。
- 真实 Qwen 仍为 `WAIVED_NOT_RUN`，不能表述为真实模型通过。
