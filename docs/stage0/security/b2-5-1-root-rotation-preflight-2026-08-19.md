# 阶段 0.3-B2-5.1 MySQL Root 密码轮换前门禁记录

执行日期：2026-08-19 Asia/Shanghai
目标实例：本机 MySQL `localhost:3306`
执行状态：通过，未修改 Root 密码、账号权限或业务数据

## 1. 证据和工作区

- B1 基线提交已存在：`6e89594 docs(security): 完成阶段0.3-B1凭据轮换准备`。
- B1 完整备份、结构备份、恢复检查和行数证据存在，SHA-256 与 B1 文档一致。
- 当前工作区包含 B2 文档、配置和只读审计脚本修改；这些是本阶段已知变更，不作为 B2-5.1 的阻断项。
- 当前仓库未发现 `.env`、`.env.local` 或可提交的真实凭据文件。
- 8123、8124 均无监听进程。

## 2. MySQL Root 状态

只读管理员连接结果：

| 项目 | 结果 |
|---|---|
| 当前认证用户 | `root@localhost` |
| MySQL 版本 | `8.0.41` |
| Root Host | `localhost` |
| Root 认证插件 | `mysql_native_password` |
| Root 已锁定 | 否 |
| Root 密码已过期 | 否 |
| Root 全局授权能力 | 仍存在 |

本步骤未执行 `ALTER USER`、`SET PASSWORD`、`CREATE USER`、`GRANT` 或任何业务数据写操作。

## 3. 业务账号状态

| 账号 | 数据库 | 认证插件 | 授权摘要 |
|---|---|---|---|
| `learning_manage_app@localhost` | `learning_manage` | `caching_sha2_password` | `SELECT, INSERT, UPDATE` |
| `learning_manage_test_app@localhost` | `learning_manage_stage0b2_test_20260818` | `caching_sha2_password` | `SELECT, INSERT, UPDATE, DELETE` |

正向连接和跨库拒绝验证均通过：

- 主库账号可连接主库，读取项目 35 条；
- 测试账号可连接隔离测试库，项目 0 条；
- 主库账号访问隔离测试库被拒绝；
- 测试账号访问主库被拒绝；
- 当前应用和测试连接不使用 Root。

## 4. 数据基线

主库：

- 用户 28、项目 35、里程碑 37、任务 122、周复盘 3；
- 活跃孤儿项目、任务、里程碑和团队成员均为 0；
- RBAC 表仍为 `tenant=1`、`role=3`、`permission=15`、`role_permission=27`、`user_role=0`。

隔离测试库：

- 20 张表；
- 用户 4、项目 0、里程碑 0、任务 0、周复盘 0；
- 活跃孤儿项目、任务、里程碑和团队成员均为 0。

## 5. 环境边界

以下 Windows 用户级变量均存在，值未输出：

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
TEST_DB_HOST
TEST_DB_PORT
TEST_DB_NAME
TEST_DB_USERNAME
TEST_DB_PASSWORD
JWT_SECRET
ALIYUN_API_KEY
```

`MYSQL_ROOT_PASSWORD` 未设置，Root 密码不进入应用环境变量。

## 6. 门禁脚本修正

`sql/audit/stage0_b2_admin_readonly_gate.sql` 原本引用结构不完整的旧 `learning_manage_test`。根据 B2-2 的实际测试目标，已改为隔离测试库 `learning_manage_stage0b2_test_20260818`，随后完整执行成功。

脚本只包含 `SELECT` 和 `SHOW` 语句。执行期间出现的 MySQL 默认认证插件弃用提示和系统变量字符集警告不影响查询结果；后续可在认证插件专项治理中处理。

## 7. 残余风险

- Root 当前仍使用 `mysql_native_password`，本步骤不与密码轮换同时迁移认证插件。
- `require_secure_transport=OFF`，当前范围限定为本机连接；生产 TLS 应作为独立安全变更处理。
- B1 已确认 Redis 5 暂不处理。

## 8. 结论

B2-5.1 通过，可以进入 B2-5.2：

1. 用户在密码管理器生成新的 Root 密码；
2. 建立保持不关闭的当前 Root 管理会话；
3. 在单独确认后执行 Root 密码修改；
4. 使用新连接验证新密码，并使用新连接确认旧密码失效。

本记录不包含任何密码、JWT、Token 或 API Key 正文。

## 9. 后续决定

完成本门禁后，用户决定本机范围暂不执行 Root 密码轮换。该决定以独立风险接受记录为准：

`b2-5-mysql-root-rotation-waiver-2026-08-19.md`

因此，本文件中的“可以进入 B2-5.2”表示技术门禁已通过，不表示 B2-5.2 已获得或已经执行。Root 密码轮换被标记为本机范围内的延期风险，后续满足重新评审条件时再执行。
