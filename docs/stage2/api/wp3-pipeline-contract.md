# WP3 AI 调用管线内部合同

状态：`IMPLEMENTED`
日期：2026-09-04

## 执行接口

```java
<T> AiExecutionResult<T> execute(AiExecutionCommand command, AiResponseProcessor<T> processor);
<T> AiExecutionResult<T> execute(AiExecutionCommand command, AiResponseProcessor<T> processor, AiFallback<T> fallback);
<T> AiExecutionResult<T> executeRaw(AiRawExecutionCommand command, AiResponseProcessor<T> processor);
```

受管命令通过 `AiPromptCodeEnum` 解析模板；`executeRaw` 只用于兼容内部 chat，调用方不能自定义场景，Pipeline 固定记录为 `legacy-chat`。文本调用固定发送 SYSTEM/USER 消息、空 Tool 列表和 `toolChoice=none`。

## 返回元数据

`AiExecutionResult` 返回业务数据、日志 ID、Trace、请求/实际模型、重试次数、耗时、finish reason、Usage、供应商请求 ID、模型回退状态以及规则降级状态。

Trace ID 允许 `[A-Za-z0-9._:-]{1,64}`；缺失时生成 32 位小写十六进制 UUID。

## 日志终态

`AiCallLogService.complete(...)` 仅更新仍为 `RUNNING` 的记录。完成命令校验成功、失败、模型回退与规则降级字段组合；降级原因写入现有 `error_message`，不新增迁移。调用日志不可用时记录告警，但不改变正常业务结果。价格、成本和完整正文治理字段在 WP6 前保持空值或 V3 默认值。

## 公共兼容

WP3 未修改 Controller 路由、请求 DTO、响应 VO 或 OpenAPI 公共契约。旧 `AiModelClient.invoke(...)` 继续存在，但业务服务不得直接调用。
