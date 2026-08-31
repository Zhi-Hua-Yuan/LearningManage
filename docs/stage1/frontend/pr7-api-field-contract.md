# PR7 前端 API 与字段合同

状态：`DRAFT（WP7-A 设计冻结候选，本地静态验收通过）`

日期：2026-08-31

## 1. 兼容基线

- 基路径：`/api`；
- 阶段 0 前端已有 37 个唯一 operation；
- PR7 不删除、不改 method、不改 path，也不把原可选字段改成必填；
- PR7 冻结 7 个新增前端调用，预期导出总数为 44；最终数量必须由契约工具计算；
- `BaseResponse` 和当前 Bearer Token 方式保持不变。

## 2. PR7 新增前端调用

| Method | Path | 用途 | 备注 |
|---|---|---|---|
| GET | `/api/team/my` | 获取当前用户有效团队及角色 | 会话级内存数据 |
| GET | `/api/team/{teamId}/members` | 获取有效团队成员 | 打开负责人选择器时懒加载 |
| GET | `/api/project/team/list` | 获取指定团队项目 | 团队展开时懒加载 |
| POST | `/api/task/assign` | 分配、转派、解除负责人 | 显式并发前置条件 |
| GET | `/api/task/{taskId}/assignment-history` | 分页获取负责人历史 | 默认 `current=1,size=50` |
| POST | `/api/task/status/change` | 幂等修改任务状态 | 不再经 `/task/update` 修改状态 |
| GET | `/api/review/team` | 分页获取团队共享摘要 | 只消费共享 VO |

PR7 不调用 `/api/team/{teamId}/leave` 或 `/api/team/member/remove`，因此两条 operation 不计入本次前端新增 7 项。

## 3. 已确认的合同勘误

### 3.1 成员移除路径

实际已验收接口为：

```http
POST /api/team/member/remove
```

请求体包含 `teamId`、`targetUserId`。旧总合同中的 `/api/team/{teamId}/member/remove` 是文档错误，不代表路由变更。

### 3.2 任务列表范围参数

当前已合并运行时 `GET /api/task/list` 只接受：

```text
projectId, status, isOverdue, current, size
```

当前 `TaskQueryRequest` 和 Controller 未实现 `assigneeUserId` 或 `assignmentScope`。PR7 不依赖这两个未实现参数：

- 无 `projectId` 时保持现有按创建人查询行为；
- 查询个人项目时使用 `projectId`；
- 查询团队项目时显式传 `projectId`，后端在项目范围返回当前成员可读任务；
- 前端需要“本人执行”口径时基于实际返回的 `assigneeUserId` 与当前用户 ID 过滤展示，不改变后端统计权威值。

未来若要增加任务范围参数，必须在新的后端工作包中实现 Controller、DTO、Service、OpenAPI 和测试后，再新增前端调用。

## 4. 任务字段

`TaskVO` 前端类型至少包含：

```text
id, projectId, milestoneId,
createdByUserId, assigneeUserId, assignedByUserId, assignedAt,
title, description, status, priority, dueDate, completedAt,
createTime, updateTime, capabilities
```

规则：

- 不继续暴露或使用含义模糊的 `userId`；
- 所有 ID 在 API 边界允许 number/string，进入页面状态后统一规范为非空字符串；
- `capabilities` 缺失、字段缺失或类型非法时使用全部拒绝的安全默认值。

## 5. 创建任务

现有 `POST /api/task/add` 增加可选字段：

```json
{
  "title": "完成权限界面",
  "projectId": 101,
  "milestoneId": null,
  "assigneeUserId": 1002,
  "priority": 2,
  "dueDate": "2026-09-05"
}
```

- 个人项目不显式选择其他用户；
- 团队项目只从当前有效团队成员中选择；
- 团队项目允许 `assigneeUserId=null`；
- 旧客户端不传时继续兼容。

## 6. 分配、转派和解除

`POST /api/task/assign` 请求：

```json
{
  "taskId": 101,
  "assigneeUserId": 1002,
  "expectedAssigneeUserId": null,
  "reason": "调整本周工作安排"
}
```

固定语义：

- `assigneeUserId=null` 表示解除分配；
- `expectedAssigneeUserId` 必须作为 JSON 属性显式提交；预期当前为空时值为 `null`，不得因 `undefined` 被省略；
- `reason` 可选，trim 后最多 200 字，不允许控制字符；
- 返回 `changed=false` 是幂等成功，前端不伪造新的历史记录；
- 返回成功后必须重新拉取任务和 capability。

响应字段：

```text
taskId, changed, previousAssigneeUserId,
assigneeUserId, assignedByUserId, assignedAt
```

## 7. 状态变更

`POST /api/task/status/change` 请求：

```json
{
  "taskId": 101,
  "targetStatus": 2,
  "clientRequestId": "UUID",
  "expectedStatus": 0
}
```

- 新的用户动作生成新的 `clientRequestId`；
- 同一网络动作的安全重试复用原 ID；
- 前端以响应 `finalStatus`、`completedAt` 为准；
- `idempotentReplay=true` 按成功处理；
- `/task/update` 请求不得包含 `status`。

## 8. 分配历史

`GET /api/task/{taskId}/assignment-history`：

```text
current 默认 1
size 默认 50
size 范围 1..100
```

每条记录只消费：

```text
id, taskId, action,
fromAssignee{userId,username},
toAssignee{userId,username},
assignedBy{userId,username},
reason, createTime
```

`action` 固定为 `INITIAL_ASSIGN`、`ASSIGN`、`REASSIGN`、`UNASSIGN`、`MEMBER_LEFT`、`MEMBER_REMOVED`。

## 9. 周复盘请求

保存请求只发送：

```text
year, weekNo, visibilityScope, teamId, focusProjectId,
reflection, nextPlan, sharedSummary, taskIds
```

更新请求只发送：

```text
id, visibilityScope, teamId, focusProjectId,
reflection, nextPlan, sharedSummary, taskIds
```

前端不提交服务端权威/派生字段：

```text
startDate, endDate, completedTaskCount, focusProjectName,
authorUserId, createTime, updateTime
```

## 10. 团队共享复盘

`GET /api/review/team?teamId=...&current=1&size=20` 只消费：

```text
id, author{id,username}, year, weekNo, startDate, endDate,
focusProject{id,name}, sharedSummary, createTime, updateTime
```

共享类型和组件禁止定义或探测 `reflection`、`nextPlan`、`taskIds` 或私人项目详情。
