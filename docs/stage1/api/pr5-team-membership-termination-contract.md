# PR5：团队成员关系终止合同

状态：`FROZEN（WP5-A）`

日期：2026-08-29

适用范围：成员主动退出、管理员移除成员，以及这些操作与任务分配/状态变更的并发行为。

## 1. 目标与边界

PR5 是“成员关系终止事务”，不是普通团队 CRUD。成员关系失效前，系统必须解除该成员在目标团队全部未完成任务上的当前受理人关系，并为每个实际解除写入不可变分配日志。

本合同不包含 OWNER 转让、团队删除、邀请码撤销、周复盘隐私、前端页面、审计新表、AI/RAG/Redis，也不修改 V1/V2。

权威依据：

- [阶段 1 需求合同](../requirements/stage1-requirements-contract.md) 的 S1-F-004；
- [权限矩阵](../authorization/permission-matrix.md) 的团队成员管理权限；
- [PR4 负责人历史查询契约](pr4-assignment-history-contract.md)；
- [ADR-005 成员终止与任务分配并发](../architecture/ADR-005-membership-termination-concurrency.md)。

## 2. API 合同

### 2.1 主动退出

```http
POST /api/team/{teamId}/leave
```

无请求体。当前登录用户是被终止的成员。

### 2.2 移除成员

```http
POST /api/team/member/remove
Content-Type: application/json
```

```json
{
  "teamId": 1001,
  "targetUserId": 2001
}
```

请求字段必须为正整数；PR5 首版不接收移除原因。

### 2.3 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "teamId": 1001,
    "memberUserId": 2001,
    "action": "MEMBER_LEFT",
    "unassignedTaskCount": 3,
    "terminatedAt": "2026-08-29T14:30:00"
  }
}
```

字段定义：

| 字段 | 定义 |
|---|---|
| `teamId` | 发生终止的团队 |
| `memberUserId` | 关系被终止的用户；移除接口不返回 `targetUserId` |
| `action` | `MEMBER_LEFT` 或 `MEMBER_REMOVED` |
| `unassignedTaskCount` | 本次实际解除的状态 0 任务数量 |
| `terminatedAt` | 同一事务使用的操作时间 |

不返回任务 ID、目标角色、邮箱、邀请码或移除原因。

## 3. 权限和拒绝语义

| 场景 | 结果 |
|---|---|
| MEMBER 主动退出 | 允许 |
| ADMIN 主动退出 | 允许 |
| OWNER 主动退出 | `40300` |
| OWNER 移除 MEMBER/ADMIN | 允许 |
| ADMIN 移除 MEMBER | 允许 |
| ADMIN 移除 ADMIN | `40300` |
| MEMBER 移除他人 | `40300` |
| 移除 OWNER | `40300` |
| 移除自己 | `40300` |
| 团队外用户、无有效成员关系、SYSTEM_ADMIN 无团队成员关系 | `40300` |
| 已失效成员再次退出或移除 | `40300` |

未登录继续使用 `40100`；参数错误使用 `40000`。拒绝消息使用通用描述，不泄漏目标原角色或成员状态。

## 4. 终止事务不变量

一次成功的终止必须满足：

```text
成员关系失效
∧ 目标团队状态 0 任务全部解除
∧ 每个实际解除任务恰好一条终止日志
∧ 任务、日志、成员关系同一事务提交
```

跨事务必须保持：

1. 状态 `1/2/3` 任务保留历史受理人，不因成员终止清空；
2. 用户重新加入团队不恢复旧任务；
3. 失效成员作为团队状态 0 任务当前受理人的数量为 0；
4. `unassignedTaskCount = 实际任务更新数 = 新增终止日志数`；
5. `task.assigned_at`、日志 `create_time`、`team_member.deleted_at` 使用同一个 `operationTime`。

## 5. 任务清理范围

内部锁定和清理查询固定为：

```sql
SELECT t.id, t.assignee_user_id
FROM task t
JOIN project p ON p.id = t.project_id
WHERE p.team_id = :teamId
  AND t.assignee_user_id = :memberUserId
  AND t.status = 0
ORDER BY t.id
FOR UPDATE
```

故意不添加 `t.is_delete = 0`、`p.is_delete = 0` 或归档过滤。逻辑删除任务和归档项目仍可能恢复使用，必须在成员终止时解除。

必须排除个人项目、其他团队、状态 `1/2/3` 任务及当前受理人不是目标成员的任务。

## 6. 审计字段

主动退出的每条日志：

```text
fromAssigneeUserId = 退出成员
toAssigneeUserId   = null
assignedByUserId   = 退出成员
action             = MEMBER_LEFT
reason             = null
```

管理员移除的每条日志：

```text
fromAssigneeUserId = 被移除成员
toAssigneeUserId   = null
assignedByUserId   = 操作管理员
action             = MEMBER_REMOVED
reason             = null
```

日志按 `task.id` 升序构造，每个实际解除任务写且只写一条。

## 7. completed→TODO 防守规则

任务从状态 `1/2/3` 重新变为 `0` 时：

- 当前受理人为 `null`：允许；
- 个人项目受理人为项目所有者：允许；
- 团队项目受理人为有效成员：锁定成员关系后允许；
- 团队项目受理人已失效：拒绝，使用 `50001`，提示先重新分配或解除受理人。

重新打开必须使用状态 CAS。与成员终止竞争时，结果只能是重新打开后随后被终止事务解除，或重新打开发现成员已失效而拒绝。

## 8. 后续工作包输入

- WP5-B 只实现成员/任务锁定、批量解除、批量日志和成员 CAS；
- WP5-C 实现终止服务、Controller、权限二次检查和 VO；
- WP5-D 将创建指定受理人、普通分配和 completed→TODO 接入同一成员锁协议；
- WP5-E 实现事务失败回滚和对账门禁；
- WP5-F 完成最终验收、风险关闭和 PR5 收口。
