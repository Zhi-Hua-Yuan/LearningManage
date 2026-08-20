# Flyway V1 基线设计

本目录记录阶段 0.4 PR 1（V1 设计和结构清单）的冻结结果。PR 1 只冻结设计、结构和参考数据边界，不引入 Flyway 依赖、不创建迁移 SQL、不连接或修改数据库。

PR 2 已完成实现，详见 [pr2-implementation.md](pr2-implementation.md)。PR 2 仍默认关闭 Flyway，不执行现有数据库接管；接管和 baseline 留给 PR 3。

PR 3 已完成隔离演练：空库 V1 迁移见 [pr3-empty-db-rehearsal-2026-08-19.md](pr3-empty-db-rehearsal-2026-08-19.md)，已有结构库显式 baseline 接管见 [pr3-existing-db-baseline-rehearsal-2026-08-19.md](pr3-existing-db-baseline-rehearsal-2026-08-19.md)。两条路径均未连接或修改现有 3306 主库。

PR 4 已完成应用配置切换与启动验证，记录见 [pr4-app-flyway-startup-2026-08-19.md](pr4-app-flyway-startup-2026-08-19.md)。应用默认不执行迁移；受控开启时必须提供独立的 `FLYWAY_DB_USERNAME` / `FLYWAY_DB_PASSWORD`，不会回退到业务应用账号。

## 文档索引

| 文档 | 用途 |
|---|---|
| [v1-design.md](v1-design.md) | V1 目标、范围、迁移策略、分工与验收门槛 |
| [v1-schema-manifest.md](v1-schema-manifest.md) | 20 张表的结构、索引和约束清单 |
| [v1-reference-data-manifest.md](v1-reference-data-manifest.md) | 固定参考数据的保留、脱敏和播种策略 |
| [v1-legacy-sql-diff.md](v1-legacy-sql-diff.md) | `sql/` 初始化脚本与审计快照的差异 |
| [pr2-implementation.md](pr2-implementation.md) | Flyway 依赖、配置、V1 DDL 和验证记录 |
| [pr3-empty-db-rehearsal-2026-08-19.md](pr3-empty-db-rehearsal-2026-08-19.md) | 空库 V1 迁移实测记录 |
| [pr3-existing-db-baseline-rehearsal-2026-08-19.md](pr3-existing-db-baseline-rehearsal-2026-08-19.md) | 已有结构库显式 baseline 接管实测记录 |
| [pr4-app-flyway-startup-2026-08-19.md](pr4-app-flyway-startup-2026-08-19.md) | 应用 Flyway 开关、账号隔离与启动验证 |

## 冻结对象

- 数据库：`learning_manage`
- 审计快照：2026-08-18，MySQL 8.0.41，20 张表
- 默认字符集/排序规则：`utf8mb4` / `utf8mb4_0900_ai_ci`
- 存储引擎：InnoDB
- V1 版本标识：`1`
- 结构快照 SHA-256：`805d0ffd9caad67417a1ece1859c33fbea7f6203c3cfcaee2aa57fab456b66f9`

## 使用约束

V1 文档以已修复的主库结构快照、`information_schema` 审计结果和已确认的阶段 0 决策为准；仓库中的旧 `sql/` 文件只作为差异输入，不是 V1 的权威来源。任何新增列、索引、外键、数据迁移或运行时配置都必须在后续 PR 中单独评审。

## PR 4 运行约束

- `FLYWAY_ENABLED` 在所有环境默认是 `false`；生产推荐由独立迁移作业先执行 `migrate`，再启动后端。
- 应用启动迁移时，`FLYWAY_DB_USERNAME` / `FLYWAY_DB_PASSWORD` 必须是受控迁移账号，不能复用仅有业务 DML 权限的 `DB_USERNAME` / `DB_PASSWORD`。
- `baseline-on-migrate=false` 保持不变；已有结构库必须先显式执行版本 1 的 `baseline`。
- `clean-disabled=true`、`validate-on-migrate=true`、`out-of-order=false` 保持不变。
- Docker Compose 不再挂载旧 `sql/` 初始化目录，避免旧脚本与 V1 迁移双重建库。
