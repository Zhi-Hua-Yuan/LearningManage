# 阶段 2 AI 调用接口合同

## 1. 保持兼容的接口

以下接口路径、HTTP 方法、必填字段和已有响应字段不得改变：

```text
POST /api/ai/breakdown
POST /api/ai/breakdown/preview
POST /api/ai/breakdown/confirm
POST /api/ai/polish
POST /api/ai/polish/preview
POST /api/ai/polish/confirm
POST /api/ai/today-order/recommend
POST /api/ai/daily-review/suggest-rename
POST /api/ai/list/replan/preview
POST /api/ai/list/replan/confirm
POST /api/ai/list/replan/cancel
POST /api/ai/draft/cancel
GET  /api/ai/draft/{draftId}
```

新增字段只能是可选字段；不得把供应商原始错误正文返回给客户端。

## 2. 新增内部接口

```java
AiChatResult chat(AiChatCommand command);
AiExecutionResult<T> execute(AiExecutionCommand command,
                             AiResponseProcessor<T> processor);
AiDraftHandler handlerFor(String scene, Integer schemaVersion);
void confirmDraft(String draftId, String operationId);
AiSanitizedContent sanitize(AiDataClassification classification, String content);
```

## 3. Pipeline 数据合同

```text
输入：userId、scene、promptCode、model、业务构造的 userPrompt、traceId
输出：类型化业务结果、actualModel、retryCount、finishReason、usage、callLogId、traceId
```

Pipeline 不返回或持久化未经脱敏的完整供应商响应。

## 4. 失败映射

| 内部失败 | 客户端业务码 | 是否回退 |
|---|---|---|
| 配置错误 | AI_CONFIG_ERROR | 否 |
| 认证/权限错误 | AI_AUTH_ERROR | 否 |
| 限流 | AI_RATE_LIMITED | 是一次 |
| 网络/超时 | AI_UPSTREAM_UNAVAILABLE / AI_TIMEOUT | 是一次 |
| 上游 5xx | AI_UPSTREAM_UNAVAILABLE | 是一次 |
| 上游协议错误 | AI_PROTOCOL_ERROR | 是一次 |
| 场景解析失败 | AI_RESPONSE_PARSE_ERROR | 否，进入规则降级 |
| 业务校验失败 | AI_BUSINESS_VALIDATION_ERROR | 否，进入规则降级 |
| 功能关闭 | AI_DISABLED | 否 |

## 5. 兼容验收

阶段 2 完成后必须重新生成运行时 OpenAPI，并确认：

```text
legacy operation：37，缺失 0
frontend operation：44，缺失 0
runtime operation：不得删除既有 operation
```
