# ADR-001：统一 AI 调用管线与可扩展模型协议

状态：`ACCEPTED`
日期：2026-09-04

## 背景

当前模型客户端只支持单轮 `system/user` 文本调用，部分 AI 场景仍由 `AiServiceImpl` 直接调用客户端。后续 Agent 需要 Tool Calling、多轮消息、Usage 和 finish reason；如果继续在各场景中自行拼装协议，会导致回退、日志和异常分类不一致。

## 决策

1. `AiInvocationPipeline` 是所有可达模型调用的唯一应用入口。
2. `AiModelClient` 增加 `chat(AiChatCommand)`，旧 `invoke(...)` 作为兼容适配器保留。
3. Pipeline 只处理 Prompt、模型调用、公共元数据、日志和异常；业务数据准备、业务校验、规则降级和正式写入由场景服务负责。
4. ModelClient 负责供应商协议、主模型/兜底模型选择和上游响应解析，不负责项目、任务和团队权限。
5. Tool Calling 协议在阶段 2 建成，但本阶段不注册业务 Tool，不开放模型动态执行能力。
6. 场景服务只能依赖 Pipeline，通过静态架构测试禁止直接依赖 ModelClient 或 Transport。

## 协议要点

```text
AiChatCommand：model、messages、tools、toolChoice、temperature、maxOutputTokens、traceId
AiChatResult：content、toolCalls、requestedModel、actualModel、finishReason、providerRequestId、usage、fallback、retryCount
```

`usage` 缺失时保持空值；旧 `invoke(...)` 只允许调用无 Tool 的文本模式并映射为 `AiInvocationResult`。

## 失败与回退

- 网络、超时、429 和 5xx：最多回退一次。
- 配置、认证、权限和参数错误：不重试。
- 上游协议错误或空响应：允许切换兜底模型一次。
- 场景解析和业务校验失败：不由 ModelClient 重试，由场景服务执行规则降级。

## 结果

该决策使现有 API 保持兼容，同时为阶段 6 的 Agent Tool Calling 复用同一模型协议和调用治理。
