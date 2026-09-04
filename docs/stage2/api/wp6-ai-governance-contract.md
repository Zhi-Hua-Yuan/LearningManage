# WP6 AI 治理内部合同

## Trace

- 请求头/响应头：`X-Trace-Id`
- 接受格式：`[A-Za-z0-9_-]{8,64}`
- 默认值：32 位小写十六进制
- 优先级：命令显式值、HTTP/MDC、随机值
- 传播目标：Pipeline、模型调用元数据、`ai_call_log`、AI 草稿和确认日志

## 安全正文

`AiContentSanitizer` 提供：

- `sanitizeForProvider`：发送供应商前删除高置信度凭据；无法安全处理时返回 `BLOCKED`。
- `sanitizeForLog`：持久化前递归处理 JSON，再执行文本规则，最后截断并计算 SHA-256。

默认正文上限 8000 字符，错误上限 2000 字符。占位符是确定性的，例如 `[REDACTED:JWT]`。普通业务语句中的 token 单词不会因缺少键值或凭据格式而被删除。

## Token 与成本

价格由 `ai.pricing` 提供，仓库不保存供应商真实价格。只要 Usage 或匹配价格不完整，`estimatedCost` 就保持 `null`。发生模型回退时按每次实际模型的已知 Usage 分别计价，再聚合到一次逻辑调用，结果采用 `decimal(20,8)` 和 `HALF_UP`。

日志列表/详情新增兼容性可选字段：Trace、请求/实际模型、供应商请求 ID、Usage、价格版本、币种、估算成本、回退、规则降级、脱敏与截断状态。统计接口增加总 Token、可估算成本、未知用量、模型回退和规则降级数量。

## 韧性和错误

- 全局并发默认 20，等待 0 ms。
- Circuit Breaker 按实际模型隔离。
- 网络、超时、429、5xx 和非法协议响应计入熔断。
- 配置、认证、调用参数和本地并发拒绝不计入熔断。
- 主模型最多一次，兜底模型最多一次；总期限耗尽后禁止下一次尝试。

公开错误：

| code | name | 语义 |
|---:|---|---|
| 30009 | `AI_DISABLED` | AI 生成功能已关闭 |
| 30010 | `AI_CONCURRENCY_LIMIT` | 本地 AI 并发容量不足 |
| 30011 | `AI_CONTENT_BLOCKED` | 内容无法安全发送 |

熔断打开继续使用 `30001 AI_SERVICE_UNAVAILABLE`。每个终态只记录一个规范化 `failure_type`。

## 配置

当 `ai.chat.enabled=true` 时，API Key、Base URL、默认模型、超时和韧性参数在启动期校验；关闭时允许缺少供应商凭据。价格目录不完整、为负数或币种非法时启动失败，价格目录完全为空时模型调用正常但成本未知。

