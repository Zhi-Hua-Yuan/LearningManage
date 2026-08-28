# ADR-003：系统角色、团队角色与租户 RBAC 边界

状态：`ACCEPTED`

日期：2026-08-23

## 背景

V1 同时包含 `user.user_role`、租户 RBAC 的 `role/permission/role_permission/user_role` 表，以及 `team_member.role`。当前应用只读写 `user.user_role` 和 `team_member.role`，没有租户 RBAC Mapper、Service 或鉴权调用方。

## 决策

阶段 1 保留三个明确边界：

| 层级 | 来源 | 值 | 用途 |
|---|---|---|---|
| 系统角色 | `user.user_role` | `USER`、`SYSTEM_ADMIN` | 平台级身份与运维入口 |
| 团队角色 | `team_member.role` | `OWNER`、`ADMIN`、`MEMBER` | 指定团队内的管理能力 |
| 资源权限 | `PermissionService` 动态计算 | 具体资源与动作 | 项目、任务、复盘、统计和 AI 授权 |

进一步决定：

1. Java 使用 `SystemRoleEnum`，不使用含义模糊的 `UserRole`。
2. V2 将小写 `user/admin` 迁移为 `USER/SYSTEM_ADMIN` 并增加应用校验和数据库检查约束。
3. 注册用户固定为 `USER`；普通用户更新资料接口不能修改系统角色。
4. `SYSTEM_ADMIN` 不默认读取或修改用户项目、任务和私人周复盘。
5. V1 租户 RBAC 五表保持结构存在但未启用，阶段 1 不写种子角色或将 `user.user_role` 自动映射进去。
6. 团队角色不能覆盖周复盘私人正文边界。

## 备选方案

### 新增 `user.system_role`

拒绝。它与现有 `user.user_role` 含义重复，会引入双写和迁移顺序问题。

### 阶段 1 全面启用租户 RBAC

拒绝。当前系统缺少租户上下文、角色管理、权限种子和应用入口，会把资源授权与平台 RBAC 同时铺开，超出阶段目标。

### SYSTEM_ADMIN 无条件绕过所有权限

拒绝。运维身份不等于私人内容读取授权；无条件绕过会破坏最小权限和后续 RAG 隔离。

## 迁移预检

允许的存量值仅为：

```text
user
admin
USER
SYSTEM_ADMIN
```

出现其他值时 V2 前置检查必须失败并生成待修复清单，不能默认映射为 USER。

## 影响

- `UserServiceImpl` 注册逻辑改用枚举；
- `UserVO` 返回规范化系统角色；
- 权限测试必须证明 SYSTEM_ADMIN 不能读取私人复盘；
- 租户 RBAC 的启用需要后续独立需求和 ADR。
