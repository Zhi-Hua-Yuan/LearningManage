# Phase 1 PR2 Review Notes

## 架构边界

- `AiModelClientImpl` 仍负责 Feature Gate、脱敏、总时限、主备切换、Usage/Cost、Bulkhead、Circuit Breaker 和结果一致性。
- `LegacyAiChatAdapter` 与 `SpringAiChatAdapter` 不访问业务服务、不自行重试、不注册全局 Tool。
- Spring AI 的 `ChatModel`、`ChatClient`、`OpenAiApi` 只在 `spring-ai` 选择器下创建；默认配置不产生模型、Memory、Embedding 或 Tool Bean。
- Tool 定义来自当前命令，且固定关闭内部 Tool 执行和并行 Tool Call。
- Streaming 返回自有 `AiStreamingChunk`；中间事件只携带增量，终止事件必须携带 finish reason、Usage、实际模型和 provider request ID。

## 风险与残余项

- Spring AI 的 provider request ID 取响应正文 `id`，不依赖额外响应头；需在真实 Qwen 补跑时确认供应商行为。
- 本阶段没有 SSE 编排和前端接入，Streaming 只验证内部契约。
- 真实 Qwen 未运行，不能将外部模型连通性、限流行为或生产延迟表述为已验证。
