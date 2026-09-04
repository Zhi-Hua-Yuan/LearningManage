# ADR-002：AI 草稿确认的 Schema 版本与草稿级幂等

状态：`ACCEPTED`
日期：2026-09-04

## 背景

当前 `ai_draft_confirm_log` 以 `(user_id,draft_id,operation_id)` 唯一。客户端重试时如果更换 operationId，数据库无法阻止同一草稿重复落库。后续 RAG/Agent 也需要复用草稿确认，但不能破坏当前任务拆解和周复盘接口。

## 决策

1. `ai_draft` 增加 `schema_version` 和 `trace_id`。
2. `ai_draft_confirm_log` 迁移前执行重复数据预审：同一草稿不同业务结果阻断迁移；相同结果保留最早记录，其余归档。
3. V3 将唯一约束改为 `(user_id,draft_id)`；`operation_id` 保留为调用审计字段。
4. 确认事务先锁定草稿，再由 Handler 重新校验当前用户、资源权限和业务状态。
5. 草稿确认、确认日志和正式业务写入在同一事务完成。
6. 任意重复 operationId 或新 operationId 都返回第一次正式结果，不重复写入。
7. `ai_replan_operation/item` 保留旧表和旧接口，但其确认流程必须使用同样的 CAS、权限重验和写入保护。

## 状态机

```text
PREVIEW → CONFIRMED
PREVIEW → CANCELED
PREVIEW → EXPIRED
```

终态不可逆。状态更新必须包含期望旧状态，确认与取消竞争由数据库条件更新决定唯一结果。

## 结果

本方案不改变现有 API 路径，却能保证同一 AI 草稿最多产生一个正式业务结果，并为未来风险分析草稿复用相同机制。
