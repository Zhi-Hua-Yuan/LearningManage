# 阶段 0.2 数据库结构差异报告

审计时间：2026-08-17 Asia/Shanghai  
数据库：`learning_manage`  
审计方式：只读 SQL、源码检索、Git 历史检索

## 1. 数据库概况

当前数据库为 MySQL 8.0.41，服务端字符集为 `utf8mb4`，排序规则为 `utf8mb4_0900_ai_ci`。数据库共有 20 张表。

仓库 `sql/` 脚本覆盖主要业务表和已有 AI 表，但数据库还存在以下 5 张未被仓库脚本覆盖的表：

```text
tenant
role
permission
role_permission
user_role
```

## 2. RBAC 表结构摘要

| 表 | 行数 | 主要用途推断 | 当前代码映射 |
|---|---:|---|---|
| tenant | 1 | 租户定义 | 未发现 |
| role | 3 | 租户角色 | 未发现 |
| permission | 15 | 资源/动作权限 | 未发现 |
| role_permission | 27 | 角色权限关联 | 未发现 |
| user_role | 0 | 用户角色关联 | 未发现 |

RBAC 表均使用 InnoDB，并包含 `is_delete`、`create_time`、`update_time` 等字段。`role` 通过 `tenant_id` 关联 `tenant`；`role_permission` 通过 `role_id` 和 `permission_id` 关联角色与权限；`user_role` 当前没有数据。

## 3. RBAC 完整性结果

| 检查 | 结果 |
|---|---:|
| 角色缺失租户 | 0 |
| 角色权限缺失角色 | 0 |
| 角色权限缺失权限 | 0 |
| 角色权限租户不一致 | 0 |
| 活跃用户 RBAC 映射 | 0 |

RBAC 自身结构和关联完整，但目前没有用户通过 `user_role` 进入该权限模型。现有应用代码使用 `user.user_role` 和 `team_member.role`，没有发现对这 5 张表的 Entity、Mapper、Service 或 Controller 映射。

## 4. 代码与历史检索

当前源码中只发现 `user.user_role` 字段；没有发现 `tenant`、`role`、`permission`、`role_permission`、`user_role` 表名的业务查询或映射。Git 历史中也没有找到这些表对应的应用实现提交，只有用户认证模块中的旧 `user_role` 字段。

## 5. V1 决策建议

当前不能直接把这 5 张表从数据库删除，也不能假设它们属于当前应用。建议先标记为“未确认所有权的遗留 RBAC 结构”：

- 若该数据库由 LearningManage 完全拥有，V1 应完整保留这些表，并在迁移文档中注明当前未接入应用代码。
- 若这些表属于外部系统或历史模块，应该明确数据库所有权，再由独立迁移或独立 Schema 管理。
- `user.user_role` 与 RBAC `user_role` 不能在没有权限设计的情况下自动映射。
- 新增 `system_role` 时不能复用现有 RBAC 表的 `role`，除非后续明确统一权限模型。

在所有权确认前，RBAC 表是 Flyway V1 的结构决策阻断项。

## 6. 决策确认

2026-08-17 已确认：

- `tenant`、`role`、`permission`、`role_permission`、`user_role` 保留为 V1 正式权限模型。
- 阶段 0 不自动迁移 `user.user_role`，也不立即接入 RBAC 业务代码。
- `team_member.role` 继续表示团队内部角色，不与系统 RBAC 角色自动合并。
- RBAC 接入和兼容迁移作为后续独立设计与实施任务。

因此，RBAC 表所有权不再阻断阶段 0 数据修复演练，但仍阻断未经设计的用户角色迁移。
