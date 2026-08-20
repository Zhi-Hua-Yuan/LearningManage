# Flyway V1 基线设计

本目录记录阶段 0.4 PR 1（V1 设计和结构清单）的冻结结果。PR 1 只冻结设计、结构和参考数据边界，不引入 Flyway 依赖、不创建迁移 SQL、不连接或修改数据库。

PR 2 已完成实现，详见 [pr2-implementation.md](pr2-implementation.md)。PR 2 仍默认关闭 Flyway，不执行现有数据库接管；接管和 baseline 留给 PR 3。

PR 3 已完成隔离演练：空库 V1 迁移见 [pr3-empty-db-rehearsal-2026-08-19.md](pr3-empty-db-rehearsal-2026-08-19.md)，已有结构库显式 baseline 接管见 [pr3-existing-db-baseline-rehearsal-2026-08-19.md](pr3-existing-db-baseline-rehearsal-2026-08-19.md)。两条路径均未连接或修改现有 3306 主库。

PR 4 已完成应用配置切换与启动验证，记录见 [pr4-app-flyway-startup-2026-08-19.md](pr4-app-flyway-startup-2026-08-19.md)。应用默认不执行迁移；受控开启时必须提供独立的 `FLYWAY_DB_USERNAME` / `FLYWAY_DB_PASSWORD`，不会回退到业务应用账号。

PR 5-A 已完成仓库侧接管准备、3306 主库只读前置审计、最新备份、真实数据隔离恢复和 baseline 演练，记录见 [pr5-a-main-takeover-preparation-2026-08-20.md](pr5-a-main-takeover-preparation-2026-08-20.md)。

PR 5-B 已完成 3306 `learning_manage` 主库 V1 显式 baseline 接管，记录见 [pr5-b-main-baseline-execution-2026-08-20.md](pr5-b-main-baseline-execution-2026-08-20.md)。主库现有业务表未被重建，`migrate` 执行数量为 0，应用默认仍关闭 Flyway。

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
| [pr5-a-main-takeover-preparation-2026-08-20.md](pr5-a-main-takeover-preparation-2026-08-20.md) | 主库接管准备、前置守卫和执行门槛 |
| [pr5-b-main-baseline-execution-2026-08-20.md](pr5-b-main-baseline-execution-2026-08-20.md) | 3306 主库 V1 显式 baseline 与后置验收 |

## 冻结对象

- 数据库：`learning_manage`
- 审计快照：2026-08-18，MySQL 8.0.41，20 张表
- 默认字符集/排序规则：`utf8mb4` / `utf8mb4_0900_ai_ci`
- 存储引擎：InnoDB
- V1 版本标识：`1`
- 结构快照 SHA-256：`805d0ffd9caad67417a1ece1859c33fbea7f6203c3cfcaee2aa57fab456b66f9`

## 使用约束

V1 文档以已修复的主库结构快照、`information_schema` 审计结果和已确认的阶段 0 决策为准；仓库中的旧 `sql/` 文件只作为差异输入，不是 V1 的权威来源。任何新增列、索引、外键、数据迁移或运行时配置都必须在后续 PR 中单独评审。

## 已发布迁移不可变策略

- 已发布迁移的机器可读哈希清单位于 src/test/resources/flyway/published-migrations.sha256。
- 当前清单只登记 V1，哈希为 E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9。
- 清单中的既有行只能追加，不能修改或删除；结构变化必须新增更高版本迁移。
- JUnit 会检查清单覆盖、文件哈希、版本唯一性和迁移文件命名。
- 基于目标分支的已有迁移增删改检查由 PR6-A 后续脚本和 PR6-B 工作流执行。

## PR 4 运行约束

- `FLYWAY_ENABLED` 在所有环境默认是 `false`；生产推荐由独立迁移作业先执行 `migrate`，再启动后端。
- 应用启动迁移时，`FLYWAY_DB_USERNAME` / `FLYWAY_DB_PASSWORD` 必须是受控迁移账号，不能复用仅有业务 DML 权限的 `DB_USERNAME` / `DB_PASSWORD`。
- `baseline-on-migrate=false` 保持不变；已有结构库必须先显式执行版本 1 的 `baseline`。
- `clean-disabled=true`、`validate-on-migrate=true`、`out-of-order=false` 保持不变。
- Docker Compose 不再挂载旧 `sql/` 初始化目录，避免旧脚本与 V1 迁移双重建库。

## PR5-A 管理入口

Windows 环境可以使用 `scripts/flyway-admin.ps1` 执行受控的 `info`、`validate`、`baseline` 或 `migrate`。脚本只读取 `DB_HOST`、`DB_PORT`、`DB_NAME`、`FLYWAY_DB_USERNAME` 和 `FLYWAY_DB_PASSWORD`；不会读取或回退到 `DB_USERNAME` / `DB_PASSWORD`。

`baseline` 还必须同时满足：

- `FLYWAY_BASELINE_AUTHORIZED=true`；
- `FLYWAY_EXPECTED_DB_NAME` 必须与本次 `DB_NAME` 完全相同；主库接管时两者均为 `learning_manage`；
- `FLYWAY_BASELINE_VERSION=1`。

主库接管前后只读 SQL 位于 `sql/flyway/stage0/main/`。原始输出必须保存在仓库外的受限目录，不得提交业务数据、密码或完整连接参数。

## 阶段 0.4 状态

V1 设计、迁移实现、空库迁移、存量库接管演练、应用开关、主库 baseline 均已完成。下一项是阶段 0 CI/Docker 门禁，不得在 CI 完成前直接开始 V2 结构迁移。
