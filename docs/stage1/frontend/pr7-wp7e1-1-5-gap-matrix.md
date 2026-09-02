# WP7-E1-1.5 缓存与会话差距矩阵

状态：`LOCAL_VALIDATED（待受保护 PR 合并）`

日期：2026-09-03

前置：WP7-E1-1.1、E1-1.2、E1-1.3 已合并；WP7-E1-1.4 前端静态门禁已完成本地验证，待受保护合并。

## 1. 目的与边界

本文件将已冻结的 15 项持久化资产和 11 项敏感内存资产路由到唯一的主责任工作包，并记录跨工作包的次责任、依赖关系和关闭证据。

E1-1.5 只冻结差距和责任，不实现 actor-scoped key、旧 key 删除、会话清理或 focus 刷新；这些分别由 E1-2、E1-3、E2 和 E3 完成。

## 2. 机器矩阵

前端机器源文件：`learning-manage-frontend/scripts/storage-gap-matrix.mjs`。

本地验证结果：

```text
assets: 26
gaps: 13
validation violations: 0
```

机器矩阵通过以下约束：

- 15 项持久化资产全部被路由；
- 11 项敏感内存资产均有主责任目标；
- 跨工作包责任允许重复引用，但每项资产的主实现责任仍由 `storage-asset-policy.mjs` 唯一决定；
- 所有依赖 gap 均存在；
- 未知资产、非法目标、重复 gap ID 和未覆盖的 implementation target 均会失败。

## 3. 差距路由

| Gap | 资产 | 当前差距 | 主责任 | 次责任 | 严重度 | 状态 |
|---|---|---|---|---|---|---|
| S7-GAP-001 | CACHE-002～004 | 全局偏好已登记 | KEEP | — | P2 | MONITORED |
| S7-GAP-002 | CACHE-014 | reload lock 为基础设施元数据 | KEEP | — | P2 | MONITORED |
| S7-GAP-003 | CACHE-005～009 | 个人资源 key 未绑定 actor | E1-2 | E1-3、E2 | P0 | OPEN |
| S7-GAP-004 | CACHE-010～012 | AI/任务草稿 key 未绑定 actor | E1-2 | E1-3、E2 | P0 | OPEN |
| S7-GAP-005 | CACHE-005～012 | 旧无账号业务 key 仍存在 | E1-3 | E1-2 | P0 | OPEN |
| S7-GAP-006 | CACHE-013 | backend version 按全 tick 前缀清理 | E1-3 | — | P1 | OPEN |
| S7-GAP-007 | CACHE-001、015 | 凭据与 session operation 未统一清理 | E2 | — | P0 | OPEN |
| S7-GAP-008 | CACHE-005～012 | actor 资源未接入会话结束清理 | E2 | E1-2、E1-3 | P0 | OPEN |
| S7-GAP-009 | MEM-001～011 | 敏感内存 reset 尚未接入会话触发器 | E2 | E3 | P0 | OPEN |
| S7-GAP-010 | MEM-003 | 团队项目需要跨页面访问裁剪 | E3 | E2 | P1 | OPEN |
| S7-GAP-011 | MEM-005 | capability reset/stale guard 仍为部分覆盖 | E3 | E2 | P0 | OPEN |
| S7-GAP-012 | MEM-008 | PRIVATE 表单 reset/stale guard 仍为部分覆盖 | E2 | E3 | P0 | OPEN |
| S7-GAP-013 | MEM-001～011 | MEMORY_ONLY 由静态策略覆盖 | KEEP | — | P1 | BASELINE_CLOSED |

## 4. 主责任规则

### E1-2：新缓存结构

- CACHE-005～009：项目、任务、聚合任务和进度必须使用 actor-scoped key；
- CACHE-010～012：AI 草稿和任务上下文必须使用 actor-scoped key；
- actor 未知时，受保护缓存必须 fail closed；
- 团队任务仍优先保持 memory-only。

### E1-3：旧 key 与版本清理

- 删除 CACHE-005～012 的旧无账号 key；
- 修正 CACHE-013 的 backend version 清理范围；
- 保留 theme、布局偏好和基础设施元数据；
- 不使用全量 `tick_`/`tick:` 前缀删除作为最终实现。

### E2：身份会话清理

- 清理 CACHE-001、CACHE-005～012、CACHE-015；
- reset MEM-001～011；
- 接入主动登出、401、token 更换和 actor 变化；
- 保持清理幂等，并阻止旧会话状态继续恢复。

### E3：上下文和迟到响应

- 处理 MEM-003 团队项目 bucket 的访问变化；
- 处理 MEM-005 capability 的 focus 刷新和最新任务替换；
- 补齐页面 context、actor、session epoch 的 stale response 防护。

## 5. 验收映射

| 证据 | 关闭目标 |
|---|---|
| storage policy scanner 60 个生产访问全部覆盖 | E1-1.4 基线 |
| gap matrix validation violations=0 | E1-1.5 完整性 |
| CACHE-005～012 账号隔离测试 | E1-2、PR7-T-044 |
| 旧业务 key 和版本清理测试 | E1-3 |
| logout/401 资源和内存 reset 测试 | E2、PR7-T-043 |
| focus、capability、迟到响应测试 | E3、PR7-T-041/045 |
| 成员/历史/共享摘要不落盘测试 | PR7-T-042 |

E1-1.5 不关闭 `S1-R-013`；该风险必须等待 E1-2、E1-3、E2、E3 的运行时证据全部完成后关闭。

## 6. 退出条件

- 26 项资产全部进入机器和人工矩阵；
- 所有 OPEN gap 有唯一主责任工作包；
- 所有跨包责任具有依赖关系；
- 文档与机器矩阵的 gap 数量、资产 ID 和主责任一致；
- 没有修改缓存运行时语义或 API operation 合同；
- 前端 `test:storage-policy`、`lint:storage-policy` 通过。
