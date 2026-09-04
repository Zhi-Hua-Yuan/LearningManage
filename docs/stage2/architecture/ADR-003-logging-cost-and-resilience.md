# ADR-003：AI 日志、数据脱敏、成本和外部调用韧性

状态：`ACCEPTED`
日期：2026-09-04

## 决策

### 日志

- 请求和响应先经过 `AiLogSanitizer`，再截断和持久化。
- 默认正文上限 8,000 字符，错误信息上限 2,000 字符。
- 保存脱敏状态、截断状态和正文 SHA-256，不保存脱敏前正文。
- 日志写入失败只告警，不改变模型调用结果。
- 通过 Trace ID 关联请求、模型调用、草稿和后续业务操作。

### 数据分级

```text
PUBLIC / INTERNAL / PRIVATE / PROHIBITED
```

API Key、JWT、密码、Authorization、Cookie 和 PROHIBITED 字段禁止进入模型或日志。私人业务内容仍可在授权场景中发送给模型，但日志必须按同一规则脱敏。

### 成本

记录 requested model、actual model、Prompt/Completion/Total Token、价格版本、币种和估算成本。Usage 或价格缺失时保持 `null`，不得伪造 0。

### 韧性

Chat 使用独立的连接超时、读取超时、Retry、Bulkhead 和 Circuit Breaker。默认最多一次模型回退；禁止无限重试。阶段 4 以后 Embedding、Rerank 和 Qdrant 复用同一策略抽象，但不在本阶段提前实现。

## 结果

该决策保证 AI 故障不会拖垮核心业务线程，同时让每次调用可以解释“用了哪个模型、花了多少 Token、是否回退以及为什么失败”。
