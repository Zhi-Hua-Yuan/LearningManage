# 阶段 0.3-B2-2 数据库环境加载与测试库验证记录

执行日期：2026-08-18 Asia/Shanghai
执行状态：通过
执行上下文：`LAPTOP-C25U74GQ\\zhiyuan` 真实 Windows 用户上下文
本阶段未执行：JWT 密钥轮换、阿里云 API Key 轮换、MySQL Root 密码轮换、Redis 变更

## 1. 环境变量加载

已从真实 Windows 用户级环境变量加载以下配置，值未输出：

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

测试数据库目标为：

```text
learning_manage_stage0b2_test_20260818
```

没有向当前 Codex 沙箱上下文复制或回显任何凭据。

## 2. 业务账号连接验证

| 账号 | 数据库 | 结果 |
|---|---|---|
| `learning_manage_app@localhost` | `learning_manage` | 连接成功 |
| `learning_manage_test_app@localhost` | `learning_manage_stage0b2_test_20260818` | 连接成功 |

连接结果确认：

- 主库项目：35；任务：122。
- 主库活跃孤儿项目：0；活跃孤儿任务：0。
- 隔离测试库项目：0；任务：0。
- 主库和测试库账号身份与目标数据库一致。

## 3. 测试库验证实例

- Profile：`test`
- 端口：`8124`
- 数据库：`learning_manage_stage0b2_test_20260818`
- 健康检查：HTTP 200
- 验证实例日志敏感关键词命中：0
- 验证完成后已停止临时实例，8124 无监听进程

所有可能产生写入的验证均指向隔离测试库，未向主库或原 `learning_manage_test` 写入数据。

## 4. 自动化测试

执行命令：

```powershell
.\\mvnw.cmd test
```

结果：

```text
Tests run: 61
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

测试日志中的 AI 降级和异常场景 WARN 属于既有测试预期，不构成测试失败。

## 5. 主库收尾复核

B2-2 完成后使用主库业务账号只读复核：

- 项目物理行数：35；
- 任务物理行数：122；
- 活跃孤儿项目：0；
- 活跃孤儿任务：0；
- 8124 临时验证实例监听数：0。

## 6. 过程说明

第一次只读连接探针使用了 MySQL 保留字作为列别名，导致语法错误；该命令未执行任何写操作。修正列别名后，主库和隔离测试库连接均成功。

本阶段没有读取、输出或保存任何密码、JWT 密钥、Token 或阿里云 API Key。下一步进入 JWT 轮换前，仍需单独确认 JWT 轮换授权；本记录不包含 JWT、阿里云 Key 或 Root 密码轮换结果。
