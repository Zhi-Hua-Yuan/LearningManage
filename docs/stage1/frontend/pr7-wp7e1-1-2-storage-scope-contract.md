# WP7-E1-1.2 Storage Scope Contract

状态：`FROZEN / IMPLEMENTED AS POLICY MANIFEST`

日期：2026-09-02

前置：`WP7-E1-1.1 Storage Asset Inventory`

本文件冻结 E1-1.1 盘点出的 15 个持久化资产和 11 个敏感内存资产的目标分类。它只定义策略，不宣称 actor 隔离、会话清理、旧 key 删除或运行时失效链已经完成。

## 1. 实现位置

机器可读策略位于前端：

```text
learning-manage-frontend/scripts/storage-asset-policy.mjs
```

策略完整性测试：

```text
learning-manage-frontend/scripts/storage-asset-policy.test.mjs
```

当前提交只增加 scope 枚举、资产策略和不变量测试；运行时缓存 key 与读写行为留给后续 E1-2/E1-3。

## 2. Scope 枚举

| Scope | 规则 |
|---|---|
| `AUTH_CREDENTIAL` | 认证凭据由身份生命周期管理，不能与普通资源缓存混用 |
| `GLOBAL_PREFERENCE` | 与账号无关的本机 UI 偏好，会话结束和后端版本变化均保留 |
| `ACTOR_RESOURCE` | 项目、任务等服务端资源，目标 key 必须包含 actorId |
| `ACTOR_DRAFT` | 用户草稿或 AI 业务状态，目标 key 必须包含 actorId |
| `SESSION_OPERATION` | 单次业务操作恢复状态，必须绑定会话并在会话结束时清理 |
| `INFRASTRUCTURE` | 后端缓存版本和 reload lock 等基础设施元数据 |
| `MEMORY_ONLY` | 敏感状态只能存在内存，不得写入 localStorage 或 sessionStorage |

## 3. 策略字段

每项策略必须包含：

```text
id
name
currentStorage
currentKey
source
targetScope
sensitivity
persistenceAllowed
actorRequired
clearOnSessionEnd
clearOnBackendVersionChange
legacyAction
implementationTarget
rationale
```

`MEMORY_ONLY` 资产还必须包含：

```text
staleGuardRequired: true
resetTarget
```

不允许使用 `TBD`、`UNKNOWN` 或空 rationale。

## 4. 持久化资产冻结决策

| Asset ID | Target scope | Actor | 会话清理 | 版本清理 | 旧数据 |
|---|---|---:|---:|---:|---|
| S7-CACHE-001 token | AUTH_CREDENTIAL | 否 | 是 | 否 | KEEP |
| S7-CACHE-002 theme | GLOBAL_PREFERENCE | 否 | 否 | 否 | KEEP |
| S7-CACHE-003 sidebar width | GLOBAL_PREFERENCE | 否 | 否 | 否 | KEEP |
| S7-CACHE-004 detail width | GLOBAL_PREFERENCE | 否 | 否 | 否 | KEEP |
| S7-CACHE-005 selected project | ACTOR_RESOURCE | 是 | 是 | 是 | DROP |
| S7-CACHE-006 project list | ACTOR_RESOURCE | 是 | 是 | 是 | DROP |
| S7-CACHE-007 project progress | ACTOR_RESOURCE | 是 | 是 | 是 | DROP |
| S7-CACHE-008 project task list | ACTOR_RESOURCE | 是 | 是 | 是 | DROP |
| S7-CACHE-009 aggregate task list | ACTOR_RESOURCE | 是 | 是 | 是 | DROP |
| S7-CACHE-010 AI planner draft | ACTOR_DRAFT | 是 | 是 | 否 | DROP |
| S7-CACHE-011 today AI order | ACTOR_DRAFT | 是 | 是 | 是 | DROP |
| S7-CACHE-012 list replan state | ACTOR_DRAFT | 是 | 是 | 是 | DROP |
| S7-CACHE-013 backend cache version | INFRASTRUCTURE | 否 | 否 | 否 | KEEP |
| S7-CACHE-014 reload lock | INFRASTRUCTURE | 否 | 否 | 否 | KEEP |
| S7-CACHE-015 confirm operation | SESSION_OPERATION | 是 | 是 | 否 | DROP |

### 关键解释

- `tick_themeMode`、`tick_sidebarWidth`、`tick_detailWidth` 是全局偏好，不因登出或后端缓存版本变化清理。
- 个人项目、任务、进度和聚合任务缓存目标上必须账号化；当前仍是旧 key，E1-2 才实施迁移。
- 旧版无 actor 的业务缓存统一 `DROP`，不把无法确认归属的数据迁移给新账号。
- AI planner 草稿包含用户输入，目标是账号隔离；主动登出时清理由 E2 接入。
- AI confirm operationId 虽是 UUID，但 key 含 draftId，属于会话业务操作，目标是账号化并在会话结束时清理。

## 5. 敏感内存资产冻结决策

`S7-MEM-001`～`S7-MEM-011` 全部冻结为：

```text
targetScope: MEMORY_ONLY
persistenceAllowed: false
clearOnSessionEnd: true
staleGuardRequired: true
```

覆盖：当前用户、团队与角色、团队项目、团队成员、Task capabilities、负责人历史、团队共享复盘、PRIVATE 复盘正文、AI requestMeta、AI responsePayload、负责人选择和 reason。

具体 resetTarget 以机器策略清单为准。E1-1.2 不新增 reset 实现，E2/E3 负责接入。

## 6. 旧数据和版本策略

旧版全局业务 key 不迁移：

```text
tick_selectedProjectId
tick:cache:project-list:*
tick:cache:project-progress:v2
tick:cache:task-list:v1:*
tick:cache:task-list:all:v1
tick_aiPlannerDraft_v1
tick:cache:task-today-ai-order:v1
tick:cache:task-list-replan-state:v1
ai:draft:confirm-operation:*
```

后续 E1-2/E1-3 必须遵守：

- actor 未确定时不得读写 `ACTOR_RESOURCE`、`ACTOR_DRAFT` 和 `SESSION_OPERATION`；
- 新 key 必须包含 actorId 或等价的会话 owner；
- 旧业务 key 只能删除，不能无条件复制；
- `GLOBAL_PREFERENCE` 和 `INFRASTRUCTURE` 不得被业务缓存清理逻辑误删。

## 7. 机器测试门禁

```text
npm run test:storage-policy
```

测试必须证明：

- 26 个资产 ID 完整且不重复；
- 所有资产都有闭合分类；
- `MEMORY_ONLY` 永远不可持久化；
- `GLOBAL_PREFERENCE` 不要求 actor 且不因会话/版本清理；
- `ACTOR_RESOURCE`、`ACTOR_DRAFT` 和 `SESSION_OPERATION` 要求 actor；
- `SECRET` 资产只能是 AUTH_CREDENTIAL 且必须会话清理；
- 基础设施元数据与资源失效策略分离。

## 8. 边界与后续工作

本合同不关闭：

- `PR7-T-042`：需要 E2 的运行态不落盘证据；
- `PR7-T-043`：需要 E2 接入 logout/401；
- `PR7-T-044`：需要 E1-2/E3 的多账号和迟到响应测试；
- `PR7-T-045`：由 E3 实现 focus 刷新；
- `S1-R-013`：继续保持 `OPEN`。

下一主目标：`WP7-E1-1.3 敏感内存资产生命周期与 reset 责任冻结`。
