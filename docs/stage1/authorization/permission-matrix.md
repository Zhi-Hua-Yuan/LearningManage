# 阶段 1 权限矩阵

状态：`FROZEN`

版本：1

## 1. 判定输入

权限计算只使用服务端可信数据：

```text
当前登录用户
+ user.user_role
+ 资源当前状态
+ project.user_id / project.team_id
+ 有效 team_member.role
+ task.user_id / task.assignee_user_id
+ weekly_review.user_id / visibility_scope / team_id
```

客户端提交的角色、创建人、团队 ID、`capabilities` 均不得作为授权事实。

## 2. 项目权限

| 动作 | 个人项目所有者 | 团队 OWNER | 团队 ADMIN | 团队 MEMBER | 团队外用户 | SYSTEM_ADMIN |
|---|---:|---:|---:|---:|---:|---:|
| PROJECT_VIEW | 允许 | 允许 | 允许 | 允许 | 拒绝 | 不默认允许 |
| PROJECT_CREATE_TASK | 允许 | 允许 | 允许 | 拒绝 | 拒绝 | 不默认允许 |
| PROJECT_UPDATE | 允许 | 允许 | 允许 | 拒绝 | 拒绝 | 不默认允许 |
| PROJECT_ARCHIVE | 允许 | 允许 | 允许 | 拒绝 | 拒绝 | 不默认允许 |
| PROJECT_DELETE | 允许 | 允许 | 允许 | 拒绝 | 拒绝 | 不默认允许 |
| PROJECT_MEMBER_LIST | 不适用 | 允许 | 允许 | 允许 | 拒绝 | 不默认允许 |

个人项目永远不因用户同属某团队而变成团队资源。

## 3. 任务权限

| 动作 | 个人项目所有者 | 团队 OWNER | 团队 ADMIN | MEMBER 且为受理人 | MEMBER 非受理人 | 团队外用户 |
|---|---:|---:|---:|---:|---:|---:|
| TASK_VIEW | 允许 | 允许 | 允许 | 允许 | 允许 | 拒绝 |
| TASK_CREATE | 允许 | 允许 | 允许 | 拒绝 | 拒绝 | 拒绝 |
| TASK_EDIT_CONTENT | 允许 | 允许 | 允许 | 允许 | 拒绝 | 拒绝 |
| TASK_CHANGE_STATUS | 允许 | 允许 | 允许 | 允许 | 拒绝 | 拒绝 |
| TASK_REORGANIZE | 允许 | 允许 | 允许 | 拒绝 | 拒绝 | 拒绝 |
| TASK_ASSIGN | 允许 | 允许 | 允许 | 拒绝 | 拒绝 | 拒绝 |
| TASK_DELETE | 允许 | 允许 | 允许 | 拒绝 | 拒绝 | 拒绝 |
| TASK_ASSIGNMENT_HISTORY_VIEW | 允许 | 允许 | 允许 | 允许 | 允许 | 拒绝 |

字段归类：

- `TASK_EDIT_CONTENT`：`title`、`description`、`dueDate`；
- `TASK_CHANGE_STATUS`：只通过状态变更接口；
- `TASK_REORGANIZE`：`priority`、`milestoneId`；
- `TASK_ASSIGN`：`assigneeUserId`，只通过分配接口。

后端必须按实际发生变化的字段校验动作，不能因为请求 DTO 同时定义了字段就默认全部允许。

## 4. 团队成员管理权限

| 动作 | OWNER | ADMIN | MEMBER |
|---|---:|---:|---:|
| 查看成员 | 允许 | 允许 | 允许 |
| 修改 MEMBER 角色 | 允许 | 拒绝 | 拒绝 |
| 修改 ADMIN 角色 | 允许 | 拒绝 | 拒绝 |
| 移除 MEMBER | 允许 | 允许 | 拒绝 |
| 移除 ADMIN | 允许 | 拒绝 | 拒绝 |
| 移除 OWNER | 拒绝 | 拒绝 | 拒绝 |
| 主动退出 | 阶段 1 拒绝 | 允许 | 允许 |

阶段 1 不实现 OWNER 转让。OWNER 若需退出，必须等待后续显式所有权转让能力。

## 5. 周复盘权限

| 动作 | 作者 | 指定团队 OWNER | 指定团队 ADMIN | 指定团队 MEMBER | 其他用户 | SYSTEM_ADMIN |
|---|---:|---:|---:|---:|---:|---:|
| REVIEW_FULL_VIEW | 允许 | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 拒绝 |
| REVIEW_UPDATE | 允许 | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 拒绝 |
| REVIEW_DELETE | 允许 | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 拒绝 |
| PRIVATE_REVIEW_DISCOVER | 允许 | 拒绝 | 拒绝 | 拒绝 | 拒绝 | 拒绝 |
| TEAM_SUMMARY_VIEW | 允许 | 允许 | 允许 | 允许 | 拒绝 | 不默认允许 |

补充规则：

- 团队共享接口只返回 `WeeklyReviewSharedVO`。
- `WeeklyReviewSharedVO` 不定义 `reflection`、`nextPlan` 和私人关联资源字段。
- 作者退出团队后仍可查看自己的完整复盘；团队当前有效成员仍可读取已发布摘要，直到作者将其改为 PRIVATE 或删除。
- 作者退出后更新复盘时不能继续提交该团队的 TEAM 状态，但允许改为 PRIVATE。

## 6. 统计与 AI 权限

| 场景 | 要求 |
|---|---|
| 用户完成任务统计 | 按 `assignee_user_id` 统计 |
| 项目管理统计 | 先要求项目管理权限 |
| 今日任务排序 | 批量校验全部 taskId；任一越权则整体拒绝 |
| 清单重排 | 要求项目管理权限；成员只读权限不足 |
| 周复盘润色 | 批量校验任务可读权，只向模型发送已验证资源 |
| AI 草稿确认 | 在确认事务中重新鉴权，不复用预览时结果 |

## 7. 拒绝语义

- 未登录：业务码 `40100`。
- 已登录但无动作权限：业务码 `40300`。
- 错误消息使用“无权限执行该操作”等通用描述，不返回目标团队角色、私人复盘作者或其他敏感细节。
- 阶段 1 保持现有 `BaseResponse` 和 HTTP 兼容方式，不在本阶段统一改造 HTTP 状态码。

## 8. N+1 门禁

列表和批量来源场景必须提供批量方法，例如：

```text
resolveProjectScopes(actorId, projectIds)
filterReadableTaskIds(actorId, taskIds)
resolveTaskCapabilities(actorId, tasks)
```

禁止在 `records.stream().map(...)` 中逐条查询项目或 `team_member`。100 个资源的权限查询必须保持常数级 SQL 次数，并在 PR3/PR4 中增加查询次数测试或等价证据。
