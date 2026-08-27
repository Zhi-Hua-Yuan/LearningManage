# PR3 权限核心本地验收记录

日期：2026-08-27
范围：`SystemRole`、统一 `PermissionService` 和核心服务接入

## 1. 已完成

- 新增 `SystemRole`，统一支持 V1 legacy 值 `user/admin` 和 V2 canonical 值 `USER/SYSTEM_ADMIN`。
- 新增 `PermissionDeniedException`，所有权限拒绝统一映射 `40300`，不泄露资源所有者或团队角色。
- 新增 `ProjectAccessScope` 和 `PermissionService`，覆盖项目、任务、周复盘和批量任务可读性判断。
- 批量权限路径使用批量任务/项目读取及一次团队成员范围解析，避免按资源逐条查询。
- 接入用户注册/登录、项目管理、任务详情/编辑/状态/删除、周复盘私密访问和统计受理人维度。
- V2 任务和周复盘实体已补齐受理人、分配元数据和可见性字段。

## 2. 权限测试

| 场景 | 结果 |
|---|---|
| system role legacy/canonical 归一化 | PASS |
| 个人项目所有者与团队外用户 | PASS |
| 团队 MEMBER 查看但不能管理项目 | PASS |
| 任务受理人可修改内容/状态但不能调整结构 | PASS |
| 非受理人 MEMBER 修改任务 | PASS（拒绝） |
| SYSTEM_ADMIN 不绕过业务资源权限 | PASS（拒绝） |
| PRIVATE/TEAM 周复盘边界 | PASS |
| 批量任务可读性解析 | PASS |

## 3. 验证结果

- Maven：113 项通过，失败 0、错误 0、跳过 0。
- PR3 权限单元测试：11 项通过。
- Flyway/CI 静态合同：通过。

## 4. 明确边界

任务列表的 `ASSIGNED_TO_ME/ACCESSIBLE` 查询、任务分配与历史写入、成员退出原子解除分配、TEAM 周复盘写入以及 AI 全量资源入口将在 PR4-PR6 中继续接入；本记录不宣称这些后续能力已经完成。
