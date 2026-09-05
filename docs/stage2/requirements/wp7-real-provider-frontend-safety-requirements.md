# WP7 需求合同：真实模型验证与前端 AI 安全闭环

状态：`FROZEN`

## 目标

1. 以受控、可审计且不向普通 CI 注入密钥的方式验证真实 `qwen-plus` 文本、Usage、请求 ID 和 Tool Calling 协议。
2. 让现有前端 AI 场景统一展示安全错误语义和可复制 TraceId。
3. 将所有模型输出限制为纯文本渲染，并用静态门禁阻止危险 DOM API 回归。

## 约束

- 不新增或修改公共 REST API。
- 不新增数据库迁移，Flyway 头保持 V3。
- 不新增 RAG、Embedding、Qdrant、Agent Tool、Markdown 渲染或 AI 直接写入。
- 真实模型验证仅由 `workflow_dispatch` 在受保护 Environment 中运行。
- 普通 CI 和跨仓候选继续使用确定性 Stub。
- WP7 只验证协议兼容性，不把回答质量作为阶段 2 发布门槛。

受保护 Environment 名称固定为 `stage2-real-provider`。其中 Secret 为 `ALIYUN_API_KEY`；Variables 为 `AI_REAL_PROVIDER_MODEL`、`AI_PRICE_VERSION`、`AI_PRICE_CURRENCY`、`QWEN_PLUS_INPUT_PRICE` 和 `QWEN_PLUS_OUTPUT_PRICE`。供应商 URL 在工作流中固定为 DashScope 华北 2 OpenAI-compatible HTTPS 地址，禁止通过 Environment 改写；模型固定验证 `qwen-plus`，价格版本和两项单价必须显式配置。

## 真实供应商验收

- 连续三轮执行中文文本、强制 Tool Call、Tool Result 回传，共九个场景。
- 每个场景必须返回非空请求 ID 和完整非负 Usage，且不得使用 fallback。
- Tool Call 只能调用 `stage2_protocol_probe`，参数必须为 JSON Object。
- Tool Call 是否存在以通过强类型校验的 `message.tool_calls` 为准。DashScope 实测在强制工具调用时可能返回 `finish_reason=stop`；适配层仅对同时存在合法 Tool Call 的 `stop` 兼容，不接受其他不一致值，证据保留供应商原始 finish reason。
- 证据仅保存模型、Usage、成本、finish reason、延迟、SHA 和请求 ID 哈希。
- API Key、Authorization、完整 Prompt、完整响应和原始请求 ID不得进入证据。

## 前端验收

- `ApiRequestError` 从响应 Header 获取经过规范化的 TraceId。
- AI 错误码统一映射为用户动作：重试、编辑输入、刷新状态或联系管理员。
- 前端不得自动重试 AI 生成或写操作。
- 模型文本只能通过 Vue 文本节点显示，禁止 `v-html`、`innerHTML` 和 `insertAdjacentHTML`。
- 所有原有 AI 页面、测试、API operation 和构建门禁保持兼容。

## 完成条件

- 真实模型工作流三轮通过并生成脱敏机器证据。
- 前端 TraceId、错误映射、安全文本和静态门禁测试通过。
- 精确后端/前端 SHA 的跨仓候选门禁通过。
- `S2-R-003` 有完整证据后关闭；`S2-A-012` 和 `S2-R-008` 保持到 WP8。
