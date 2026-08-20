# 后端 CI 脚本

本目录存放 PR6-A 提供的 Linux CI 脚本。PR6-B 的 GitHub Actions 只负责编排，不重复实现数据库验证逻辑。

## 计划脚本

| 脚本 | 用途 | 状态 |
|---|---|---|
| `assert-ci-database-target.sh` | 拒绝主库和未授权数据库目标 | 未实现 |
| `wait-for-mysql.sh` | 等待临时 MySQL 可用 | 未实现 |
| `provision-ci-databases.sh` | 创建 CI 数据库和隔离账号 | 未实现 |
| `verify-empty-database.sh` | 验证空库 V1 迁移路径 | 未实现 |
| `verify-existing-database.sh` | 验证存量库 baseline 路径 | 未实现 |
| `verify-published-migrations.sh` | 验证已发布迁移不可修改 | 未实现 |

## 统一约束

- 使用 `set -Eeuo pipefail`。
- 禁止 `set -x`。
- 不接受命令行密码。
- 所有数据库脚本先执行目标保护。
- 只允许 `learning_manage_ci_*` 数据库。
- 禁止连接或修改 `learning_manage` 主库。
- 禁止执行 Flyway `clean` 和 `repair`。
- 失败必须返回非零退出码。
- 输出采用 `key=value` 格式。
- 日志不得包含密码、Token 或完整凭据。

## 当前状态

A1 仅建立目录说明，脚本将在 A4 实现。
