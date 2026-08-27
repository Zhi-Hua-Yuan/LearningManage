# 阶段 1 API 兼容合同

状态：`PROPOSED`

基线路径：`/api`

阶段 0 前端基线：37 个唯一 operation，缺失 0

## 1. 兼容原则

1. 原 37 个前端 operation 的 method + path 必须全部保留。
2. 新增前端调用必须进入契约导出，并在运行时 OpenAPI 中全匹配。
3. 阶段 1 的验收口径是“原有缺失数 0 + 新增缺失数 0”，不是总数继续等于 37。
4. 保持 `BaseResponse` 结构和当前认证头方式。
5. 不在阶段 1 批量重命名旧路径或改造全部 HTTP 状态码。
6. 数据库实体不作为新增或改造接口的 Request/Response 类型。

## 2. 现有接口扩展

### `POST /api/task/add`

新增可选字段：

```json
{
  "assigneeUserId": 1002
}
```

- 个人项目为空时默认项目所有者，显式值只能是所有者；
- 团队项目为空表示未分配，非空必须是有效团队成员；
- 旧客户端不传该字段仍可工作。

### `POST /api/task/update`

- 不接受 `assigneeUserId`；
- 状态仍使用独立状态接口；
- 后端根据请求实际变化字段分别校验 `TASK_EDIT_CONTENT` 或 `TASK_REORGANIZE`。

### `GET /api/task/list`

新增可选参数：

```text
assigneeUserId
assignmentScope=CREATED_BY_ME|ASSIGNED_TO_ME|ACCESSIBLE
```

未传 `assignmentScope` 时保持旧行为 `CREATED_BY_ME`，避免现有页面结果集突然扩大。团队页面必须显式使用 `ACCESSIBLE`，我的执行任务使用 `ASSIGNED_TO_ME`。

### 周复盘现有接口

`/review/current`、`/review/save`、`/review/update`、`/review/{id}`、`/review/history` 路径保留。请求和响应从实体切换为 DTO/VO，并增加可选字段：

```text
visibilityScope
teamId
focusProjectId
sharedSummary
taskIds
```

旧客户端未传时按 PRIVATE 处理。

## 3. 新增后端 operation

| Method | Path | 权限 | 用途 |
|---|---|---|---|
| POST | `/api/task/assign` | `TASK_ASSIGN` | 分配、转派或解除受理人 |
| GET | `/api/task/{taskId}/assignment-history` | `TASK_ASSIGNMENT_HISTORY_VIEW` | 查询任务分配历史 |
| POST | `/api/team/{teamId}/leave` | 有效成员且非 OWNER | 主动退出团队 |
| POST | `/api/team/{teamId}/member/remove` | OWNER 或受限 ADMIN | 移除成员 |
| GET | `/api/review/team` | 指定团队有效成员 | 查询团队共享摘要 |

后端已存在 `GET /api/team/{teamId}/members`；阶段 1 前端新增调用，但不重复新增后端路由。

## 4. 关键 DTO/VO

### `TaskAssignRequest`

```json
{
  "taskId": 101,
  "assigneeUserId": 1002,
  "expectedAssigneeUserId": 1001,
  "reason": "调整本周工作安排"
}
```

`assigneeUserId=null` 表示解除分配。`expectedAssigneeUserId` 必须能区分“未提供并发条件”和“预期当前为空”；具体 JSON 表达由 PR4 在不歧义的前提下固定，可使用额外 `expectedVersion` 替代。

PR4 固定采用 `expectedAssigneeProvided=true` 表示调用方明确预期当前无人受理；当
`expectedAssigneeUserId` 为非空时视为已提供，未传两个字段则不启用受理人并发条件。

### `TaskVO.capabilities`

```json
{
  "canEditContent": true,
  "canChangeStatus": true,
  "canReorganize": false,
  "canAssign": false,
  "canDelete": false
}
```

### `WeeklyReviewSaveRequest`

```json
{
  "year": 2026,
  "weekNo": 34,
  "visibilityScope": "TEAM",
  "teamId": 20,
  "focusProjectId": 101,
  "reflection": "私人正文",
  "nextPlan": "私人计划",
  "sharedSummary": "本周完成权限设计，下一周实施迁移",
  "taskIds": [1001, 1002]
}
```

### `WeeklyReviewSharedVO`

只允许：

```text
id, author summary, year, weekNo, startDate, endDate,
focusProject safe summary, sharedSummary, createTime, updateTime
```

禁止定义：

```text
reflection, nextPlan, private task list, private project data
```

## 5. 错误与并发合同

| 场景 | 业务码 |
|---|---:|
| 未登录 | 40100 |
| 已登录但动作无权限 | 40300 |
| 参数、枚举、目标成员不合法 | 40000 |
| 资源不存在 | 40400 |
| 并发状态已变化 | 50001 |

并发冲突消息必须提示刷新重试，不返回他人私人数据或完整成员状态。

## 6. 契约验收

- 保存阶段 0 的 37 operation 集合作为兼容子集；
- 运行时 OpenAPI 包含该子集；
- 阶段 1 前端导出的全部 operation 在运行时存在；
- `missingOperationCount=0`；
- 旧客户端不传新增可选字段时，个人项目/任务/PRIVATE 周复盘仍可运行。
