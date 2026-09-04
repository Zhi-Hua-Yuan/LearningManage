# WP5 草稿生命周期内部合同

状态：`IMPLEMENTED / CANDIDATE CI PENDING`

## 公共 API 兼容边界

以下接口路径、请求字段和成功响应结构保持不变：

```text
POST /api/ai/breakdown/confirm
POST /api/ai/polish/confirm
POST /api/ai/draft/cancel
GET  /api/ai/draft/{draftId}
POST /api/ai/list/replan/confirm
POST /api/ai/list/replan/cancel
```

通用确认成功仍返回：

```json
{
  "success": true,
  "idempotentReplay": false,
  "businessId": 1001
}
```

相同草稿任意重试返回首次 `businessId`，并将 `idempotentReplay` 设置为 `true`。

## 内部接口

```text
AiDraftConfirmationService.confirm(command)
AiDraftHandler.apply(draft, typedContext)
AiDraftHandlerRegistry.require(scene, context)
AiDraftStateMachine
AiReplanWriteGuard
```

创建草稿使用 `AiDraftCreateCommand`，必须携带当前场景 Schema 版本和生成调用 Trace ID。

## 失败语义

| 场景 | 结果 |
|---|---|
| 草稿不属于当前用户或场景不匹配 | `NOT_FOUND_ERROR` |
| 草稿已取消 | `AI_DRAFT_NOT_CONFIRMABLE` |
| 草稿已过期 | `AI_DRAFT_EXPIRED` |
| Schema 不受支持 | `AI_DRAFT_SCHEMA_UNSUPPORTED` |
| 终态与日志不一致、CAS 异常 | `AI_DRAFT_CONFLICT` |
| 已确认且确认日志一致 | 成功重放首次结果 |

未知状态、已确认但缺少日志、日志与场景或终态不一致时一律失败关闭。

## 数据库边界

- 使用 V3 的 `(user_id,draft_id)` 唯一约束。
- `operation_id` 只保存首次成功请求的审计值。
- `trace_id` 从生成草稿贯穿到确认日志。
- WP5 不产生任何 Flyway 新迁移。

清单重排确认对每个变更任务执行以下原子条件：任务仍属于原项目、仍为 TODO、未删除，且标题、优先级、截止日期和 `update_time` 均与预览快照一致。任一任务不匹配时，整次重排回滚并保持操作为 `PREVIEW`。
