# Phase 2 Review Notes

## 架构边界

- `AiInvocationPipeline` 仍是唯一业务调用入口；结构化解码器只负责格式说明和反序列化，不负责重试、权限、资源写入或 fallback 决策。
- `BeanOutputConverter` 使用严格 Jackson mapper：未知字段和缺失必填 creator property 归类为 `RESPONSE_SCHEMA`；非法 JSON 归类为 `RESPONSE_PARSE`。
- 五个场景的业务解析器继续负责数量、日期、ID、资源状态和 Draft 边界，符合“结构严格、业务兼容”的迁移策略。
- `task-breakdown` 的包装只存在于模型内部结构，外部 VO 和 Draft payload 没有迁移。

## 可观测性

- `learning.ai.invocations.failure_type` 区分 `RESPONSE_PARSE`、`RESPONSE_SCHEMA` 和 `BUSINESS_VALIDATION`。
- AI 路由上的权限拒绝与资源状态冲突记录到 `learning.ai.scene.outcomes`，标签只使用场景和固定 outcome，不包含 ID、Prompt 或响应内容。
- 调用日志继续由既有 `AiCallLogService` 完成，provider request ID、usage、cost、模型和 finish reason 不由 converter 接管。

## 风险与残余项

- 当前只验证 Spring AI converter 在确定性测试输入上的行为，未把 provider-native schema 选项宣称为可用。
- 真实 Qwen 尚未执行，不能将真实模型兼容性、延迟、限流或响应字段行为表述为已通过。
- 结构化解码不自动重试；如后续需要修复模型输出，应提交单独的治理策略变更和门禁证据。
