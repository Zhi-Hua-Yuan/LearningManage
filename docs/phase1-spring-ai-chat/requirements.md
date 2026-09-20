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
