# V2 迁移备份与恢复运行手册

状态：PR2 可执行合同

适用迁移：`V1 → V2`

## 1. 恢复策略

V2 包含列重命名、数据回填和新表创建，不提供破坏性逆向 SQL。失败处理分为两个时间点：

- 应用切换前失败：停止发布，不让应用连接 V2；从迁移前逻辑备份恢复到新数据库并核对 V1 结构和数据。
- 应用切换后发现问题：隔离受影响环境，保留 V2 现场和备份；默认通过新版本 Flyway 迁移前向修复。只有完成变更审批和数据差异评估后，才允许切换到恢复出的 V1 数据库。

禁止修改已发布的 V1、V2 迁移文件，也禁止在原数据库上执行未经演练的逆向 DDL。

## 2. 自动化演练

[恢复 Gate](../../../scripts/ci/verify-v2-recovery.sh) 在 MySQL 8.0.41 一次性环境内执行以下顺序：

1. 创建隔离的 V1 源库和恢复目标库；
2. 导入冻结 V1 schema 与阶段 1 fixture，并记录 Flyway V1 baseline；
3. 用 `mysqldump --single-transaction` 生成迁移前逻辑备份和 SHA-256；
4. 对源库执行 25 项 preflight、V2 迁移和 12 项 post-verify；
5. 将迁移前备份恢复到新的空目标库；
6. 核对目标库仍为 V1：20 张业务表、21 张总表、20 条 fixture 业务记录、V1 baseline，以及旧 `assignee_id` 列；
7. 断言目标库不存在 V2 的 `assignee_user_id`、`task_assignment_log` 和 `weekly_review_task`。

演练数据库名称必须满足 CI 白名单，主机必须是 `127.0.0.1`，端口不得为 `3306`。脚本不会连接仓库外数据库，不会执行 `DROP DATABASE`，临时 SQL 备份在 Gate 退出时删除，仅在日志中保留校验和与计数证据。

## 3. CI 调用

恢复 Gate 已接入：

- `.github/workflows/backend-ci.yml` 的 `flyway-existing`；
- `.github/workflows/release-gate.yml` 的 `flyway-existing`。

它依赖同一 job 先执行数据库等待和账号初始化：

```bash
bash scripts/ci/wait-for-mysql.sh
bash scripts/ci/provision-ci-databases.sh
bash scripts/ci/verify-v2-recovery.sh
```

成功日志至少包含：

```text
recovery.verify.success=true
recovery.backup.sha256=<64 位大写 SHA-256>
recovery.source.version=2
recovery.restored.version=1
recovery.restored.business_tables=20
recovery.restored.business_rows=20
```

## 4. 发布前人工检查

- 确认备份时间早于 V2 迁移开始时间；
- 确认备份存储位置、保留期、加密和访问控制符合目标环境要求；
- 在独立目标库执行恢复，禁止覆盖迁移现场；
- 对比关键表计数、Flyway history、任务受理人列和抽样业务记录；
- 恢复核对失败时保持发布停止，并升级给数据库负责人；
- 任何共享或正式环境操作都必须使用该环境自己的变更审批和凭据，本仓库 CI 凭据不可复用。

## 5. 验收证据

PR2 合并前必须同时保留：

- 本地 MySQL 8.0.41 恢复演练记录；
- GitHub Actions `flyway-existing` 成功记录；
- V1/V2 发布迁移哈希验证；
- Maven 测试总数与失败、错误、跳过统计；
- `git diff --check` 和凭据模式扫描结果。
