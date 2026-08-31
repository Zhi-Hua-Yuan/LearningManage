# 阶段 1 API 兼容合同

状态：`FROZEN`

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

当前运行时支持的查询参数：

```text
projectId
status
isOverdue
current
size
```

当前接口不支持 `assigneeUserId` 或 `assignmentScope`，前端不得提前依赖未实现参数。未传 `projectId` 时保持按创建人过滤的旧行为；团队任务页面必须显式传入有权查看的团队项目 `projectId`，并根据返回的 `TaskVO.assigneeUserId` 展示负责人。若后续需要“分配给我”等跨项目筛选，必须另行修改后端、OpenAPI、兼容合同和自动化测试。

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
| POST | `/api/team/member/remove` | OWNER 或受限 ADMIN | 移除成员，`teamId` 位于请求体 |
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

`assigneeUserId=null` 表示解除分配。`expectedAssigneeUserId` 必须作为显式 JSON 属性发送：非空值表示预期当前负责人，`null` 表示预期当前未分配；省略该属性属于非法请求。该语义已由 PR4 固定，前端不得以缺字段代替显式 `null`。

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

## 7. PR7 前端调用增量

PR7 在阶段 0 的 37 个 operation 兼容子集上新增 7 个前端调用，预期总数为 44，最终以契约导出器结果为准：

| Method | Path |
|---|---|
| POST | `/api/task/assign` |
| GET | `/api/task/{taskId}/assignment-history` |
| GET | `/api/team/my` |
| GET | `/api/team/{teamId}/members` |
| GET | `/api/project/team/list` |
| GET | `/api/review/team` |
| POST | `/api/task/status/change` |

`POST /api/team/{teamId}/leave` 与 `POST /api/team/member/remove` 是已存在的后端 operation，但成员关系终止 UI 不属于 PR7，不能计入上述 7 个新增前端调用。详细字段、权限和错误合同见 [PR7 API 与字段合同](../frontend/pr7-api-field-contract.md)。
