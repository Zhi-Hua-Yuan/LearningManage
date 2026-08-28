# PR4-D1 任务负责人历史查询契约

状态：`FROZEN（D1）`
日期：2026-08-29
适用接口：`GET /api/task/{taskId}/assignment-history`

## 1. 范围

D1 只冻结查询参数、分页语义、查询行模型和对外 VO。D2 才实现 Mapper、Service、Controller、权限执行和 MySQL 验收。V1/V2 migration 不变。

## 2. 请求与分页

请求参数：

```text
taskId       路径参数，正整数
current      查询页码，默认 1；D2 校验为 >= 1
size         每页条数，默认 50；D2 校验为 1..100
```

结果使用 `BaseResponse<Page<TaskAssignmentHistoryVO>>`。记录按 `create_time DESC, id DESC` 稳定排序，避免同一时间戳下分页漂移。

## 3. 记录字段

每条记录包含：`id`、`taskId`、`action`、`fromAssignee`、`toAssignee`、`assignedBy`、`reason`、`createTime`。

`action` 取值固定为 `INITIAL_ASSIGN`、`ASSIGN`、`REASSIGN`、`UNASSIGN`、`MEMBER_LEFT`、`MEMBER_REMOVED`，与 `TaskAssignmentActionEnum` 保持一致。

用户摘要只包含：

```json
{
  "userId": 1002,
  "username": "当前展示名"
}
```

未分配时整个摘要为 `null`。用户已删除或无法关联时保留 `userId`，`username` 为 `null`；名称是当前展示名，不承诺历史快照。

## 4. 隐私与权限边界

接口由 D2 使用 `TASK_ASSIGNMENT_HISTORY_VIEW` 执行授权。授权通过后才返回 `reason`；未授权请求不得通过分页、错误信息或响应字段泄露历史内容。VO 不得包含密码、账号、角色、删除标记、任务正文、反思或计划等字段。

## 5. D1 验收口径

- 请求默认分页值为 `current=1,size=50`；
- VO/用户摘要字段白名单测试通过；
- null 负责人、已删除用户的序列化语义测试通过；
- Row 为扁平查询模型，不引用实体；
- 未新增路由实现、未修改 V1/V2 migration；
- S1-A-003 继续 `PENDING`，S1-R-014 继续 `OPEN`，待 D2 集成、权限、审计对账后再更新。
