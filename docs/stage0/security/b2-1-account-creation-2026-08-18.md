# 阶段 0.3-B2-1 MySQL 账号创建与最小权限验证记录

执行日期：2026-08-18 Asia/Shanghai
执行状态：通过
执行范围：本机 MySQL、Windows 用户级数据库环境变量
未执行：Root 密码轮换、JWT 轮换、阿里云 API Key 轮换、Redis 变更

## 1. 目标账号和数据库边界

| 账号 | Host | 允许访问 | 授权 |
|---|---|---|---|
| `learning_manage_app` | `localhost` | `learning_manage` | `SELECT, INSERT, UPDATE` |
| `learning_manage_test_app` | `localhost` | `learning_manage_stage0b2_test_20260818` | `SELECT, INSERT, UPDATE, DELETE` |

测试账号没有访问 `learning_manage` 或原 `learning_manage_test` 的权限；主库账号没有访问隔离测试库的权限。

两个账号均使用 MySQL 8 `caching_sha2_password`，未锁定且密码未过期。

## 2. 环境变量

以下变量已写入 Windows 用户级环境变量，值只在受保护的本机用户上下文中使用，不写入仓库、文档、日志或命令输出：

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
```

测试连接目标为 `learning_manage_stage0b2_test_20260818`，避免修改原 `learning_manage_test`。

## 3. 权限验证

已验证：

- 主库账号连接主库成功；
- 测试账号连接隔离测试库成功；
- 主库账号访问隔离测试库被拒绝；
- 测试账号访问主库被拒绝；
- 测试账号访问原 `learning_manage_test` 被拒绝；
- 主库账号执行 DDL 被拒绝；
- 测试账号执行 DDL 被拒绝；
- 主库账号执行物理 `DELETE` 被拒绝；
- 主库账号执行授权被拒绝；
- 测试账号的无行数 DML 权限验证通过；
- 两个账号的账号状态和认证插件符合预期。

## 4. 数据完整性复核

账号创建和权限验证没有修改业务数据：

- 主库项目行数：35；
- 主库任务行数：122；
- 主库活跃孤儿项目：0；
- 主库活跃孤儿任务：0；
- 隔离测试库项目行数：0；
- 隔离测试库任务行数：0。

## 5. 凭据安全补救

第一次验证脚本中的诊断命令曾意外回显初版业务账号密码。该密码立即按泄漏处理：

1. 重新生成两个业务账号密码；
2. 使用 Root 执行 `ALTER USER`；
3. 覆盖 Windows 用户级环境变量；
4. 删除临时环境变量探针；
5. 使用新密码重新完成全部连接和越权验证。

初版密码已失效，仓库和 B2 文档不保存任何密码正文。

## 6. 后续门槛

B2-1 仅完成业务账号和测试账号创建与验证。后续仍需单独确认和执行：

- 使用新环境变量启动应用并运行 61 项测试；
- JWT 密钥轮换；
- 阿里云 API Key 配置和最低成本验证；
- 确认应用不再依赖 Root 后轮换 Root 密码。
