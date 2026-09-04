# WP2 模型协议与供应商兼容层

状态：`IMPLEMENTED / PENDING_PR_CI`
日期：2026-09-04

## 内部接口

```java
AiChatResult chat(AiChatCommand command);
AiInvocationResult invoke(String primaryModel, String systemPrompt, String userPrompt);
```

`invoke(...)` 是文本兼容适配器。它通过 `chat(...)` 发起请求，但继续返回原有 `AiInvocationResult`，不改变 Controller 或公共 API。

## 供应商中立协议

`AiChatCommand` 包含：

```text
requestedModel、messages、tools、toolChoice、temperature、maxOutputTokens
```

`AiChatResult` 包含：

```text
content、toolCalls、finishReason、usage、providerRequestId
requestedModel、actualModel、retryCount、fallbackUsed、fallbackReason
```

消息角色支持 `SYSTEM`、`USER`、`ASSISTANT` 和 `TOOL`。工具仅支持 JSON Schema 描述的 `function`；WP2 可以表达和解析多个 Tool Calls，但不注册或执行任何业务工具。

## 校验边界

- 请求包含 1～64 条消息、最多 32 个工具。
- 函数名符合 `[A-Za-z0-9_-]{1,64}`，工具名称唯一。
- assistant 消息至少包含 content 或 Tool Calls；tool 消息必须引用此前且尚未回传结果的 Tool Call。
- 历史 Tool Call 必须引用当前已声明工具，并具有对应 tool 结果。
- Function arguments 必须是 JSON 对象字符串；parameters 必须是 JSON 对象。
- `temperature` 为 `[0, 2)`，`maxOutputTokens` 为正数。
- 当前只支持字符串 content、`stream=false` 和 `parallel_tool_calls=false`。

## OpenAI-compatible 映射

供应商适配器负责以下转换，业务服务不得自行拼装供应商 JSON：

| 内部字段 | 上游字段 |
|---|---|
| `requestedModel` | `model` |
| `messages` | `messages` |
| `tools` | `tools[].type/function` |
| `toolChoice=AUTO/NONE` | `tool_choice=auto/none` |
| `toolChoice=FUNCTION` | `tool_choice.type/function.name` |
| `maxOutputTokens` | `max_tokens` |
| assistant Tool Calls | `message.tool_calls`，适配层按数组位置生成 `index` |
| tool 结果引用 | `tool_call_id` |

响应只消费 `choices[0]`，忽略未知扩展字段。`actualModel` 优先取响应顶层 `model`，该字段缺失时才回退为本次发送的模型。Usage 缺失时保持 `null`；存在时仅要求 Token 字段为非负整数，不强制三者加和。

`providerRequestId` 提取顺序：响应 JSON 顶层 `id`、`x-request-id`、`x-dashscope-request-id`、`request-id`。HTTP Header 名大小写不敏感，Authorization、Cookie 等敏感 Header 不进入 `AiHttpResponse`。

## 失败与兼容

- 网络、超时、429、5xx 和非法协议响应沿用原有“主模型后最多一次 fallback”。
- 401、配置错误和请求参数错误不回退。
- fallback 完整保留消息、工具、工具选择和生成参数，仅替换实际模型。
- 上游非 2xx 响应正文不写入异常，避免错误载荷形成日志泄漏。
- 旧 `invoke(...)` 收到无文本的 Tool Call 响应时按 `INVALID_RESPONSE` 失败。
- WP2 不引入 Resilience4j，不改变后续 WP3/WP6 的 Pipeline、重试和熔断责任。

协议映射依据阿里云百炼的 OpenAI Chat Completions 兼容接口：<https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions>。
