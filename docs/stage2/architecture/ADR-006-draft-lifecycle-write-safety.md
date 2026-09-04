# ADR-006：AI 草稿生命周期与写入安全

状态：`ACCEPTED`
日期：2026-09-04

## 背景

V3 已将 `ai_draft_confirm_log` 的唯一边界提升为 `(user_id,draft_id)`，但旧运行时代码仍按
`operationId` 查询重放记录，并在各场景内分别执行权限检查、业务写入、确认日志和状态变更。
这会使更换 operationId 的重试、确认/取消竞争以及“标记过期后抛异常”产生不一致风险。

## 决策

1. 通用草稿确认统一进入 `AiDraftConfirmationService`，按用户、草稿和场景锁定草稿行。
2. `AiDraftHandlerRegistry` 使用 `scene` 路由强类型 Handler，并检查 Payload Schema 版本。
3. 首批注册任务拆解与周复盘润色 Handler，二者当前版本均为 1。
4. 幂等重放只按 `(userId,draftId)` 查询；operationId 保留为首次确认审计字段。
5. Handler 负责 Payload 校验、当前权限重验和正式写入；确认内核负责事务、日志和状态机。
6. 业务写入、确认日志与 `PREVIEW -> CONFIRMED` CAS 在同一事务中完成。
7. 过期转换在事务中提交后再向调用方返回错误，避免预期异常回滚过期状态。
8. 清单重排保留独立表和 API，通过 `AiReplanWriteGuard` 复用行锁、CAS 和事务结果模式；任务更新同时比较项目、旧字段和快照更新时间，任何失配都回滚全部任务。
9. 草稿和确认日志继承生成调用的 Trace ID；时间判断通过可替换 Clock 完成。

## 状态机

```text
PREVIEW ──confirm──> CONFIRMED
PREVIEW ──cancel───> CANCELED
PREVIEW ──expire───> EXPIRED
```

所有终态不可逆；CAS 失败必须重读赢家状态或失败关闭，禁止基于旧快照继续写入。

## 事务顺序

```text
锁定草稿
→ 查询草稿级确认日志
→ 校验 Schema/状态/有效期
→ Handler 权限重验与业务写入
→ 插入确认日志
→ CAS 标记 CONFIRMED
→ 提交
```

任一步失败都会回滚正式业务写入。已存在有效确认日志时直接返回首次 businessId，且
`idempotentReplay=true`。

## 兼容性

- 不新增或修改公共 API 路径。
- `AiDraftConfirmVO` 字段保持不变。
- 不新增 V4，V1/V2/V3 保持字节级不可变。
- 清单重排继续返回既有 Boolean 结果并保持终态错误语义。
