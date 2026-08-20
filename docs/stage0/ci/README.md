# 阶段 0 CI 与迁移发布门禁

状态：PR6-A A2 存量库 fixture 已完成

## 目标

建立可重复执行的后端 CI 脚本、存量库接管 fixture 和已发布迁移不可变门禁，为后续 GitHub Actions 接入提供稳定基础。

## 当前边界

- 已发布迁移的机器可读哈希清单位于 src/test/resources/flyway/published-migrations.sha256。

- 本目录不保存凭据、数据库备份或业务数据。
- PR6-A 不创建 GitHub Actions 工作流。
- 数据库验证只能针对明确授权的临时 CI 数据库。
- 禁止连接或修改 3306 `learning_manage` 主库。
- 禁止执行 `flyway clean`。
- 已发布的 V1 迁移不可修改。

## PR6 拆分

| 阶段 | 内容 | 状态 |
|---|---|---|
| PR6-A | 后端 CI 脚本、存量库 fixture、V1 不可变检查 | 进行中 |
| PR6-B | 后端 GitHub Actions 和 Docker 门禁 | 未开始 |
| PR6-C | 前端测试和前端 CI | 未开始 |
| PR6-D | 跨仓发布门禁和阶段 0 验收 | 未开始 |

## PR6-A 实施步骤

| 步骤 | 内容 | 状态 |
|---|---|---|
| A1 | 建立目录和文档骨架 | 已完成 |
| A2 | 生成并审查存量库 fixture | 已完成 |
| A3 | 实现 V1 和已发布迁移不可变检查 | 已完成 |
| A4 | 实现跨平台 CI 脚本 | 未开始 |
| A5 | 隔离 MySQL 端到端验证 | 未开始 |
| A6 | 完成记录、复核和提交 | 未开始 |

## 文档索引

- [PR6-A 执行记录](pr6-a-backend-ci-foundation-2026-08-20.md)
- [CI 脚本说明](../../../scripts/ci/README.md)
- [存量库 fixture 说明](../../../src/test/resources/db/legacy/README.md)
