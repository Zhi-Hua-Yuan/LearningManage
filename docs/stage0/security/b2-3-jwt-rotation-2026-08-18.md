# 阶段 0.3-B2-3 JWT 密钥轮换与验证记录

日期：2026-08-18
范围：本机真实 Windows 用户 `LAPTOP-C25U74GQ\\zhiyuan` 及隔离测试库
结论：通过（JWT 轮换完成；阿里云 API Key、MySQL Root 密码和 Redis 未处理）

## 1. 执行边界

本阶段只轮换 JWT 签名密钥并验证应用行为，不修改主库业务数据，不轮换阿里云 API Key、MySQL Root 密码或 Redis 凭据。所有写入型接口验证均使用隔离测试库 `learning_manage_stage0b2_test_20260818`。

## 2. 密钥处理

- 使用 `RandomNumberGenerator` 生成 64 字节随机值，并 Base64 编码为 88 个字符。
- 新值写入 Windows 用户级环境变量 `JWT_SECRET`。
- 密钥值未写入仓库、文档、终端输出或日志。
- 生产配置未改动；开发/测试配置继续从环境变量读取。

## 3. 验证过程

首次启动探针发现本机 MySQL `caching_sha2_password` 连接需要公钥检索参数；未执行任何写操作。随后仅在 `application-dev.yml` 和 `application-test.yml` 的本地 JDBC URL 增加 `allowPublicKeyRetrieval=true`，生产配置未增加该参数。

第二次探针发现 Windows 用户级变量不会自动注入到当前 Codex 启动进程；该实例未执行写操作。最终通过从 HKCU 用户范围读取变量并显式注入测试进程完成验证。

最终测试实例：

- `GET /api/health`：HTTP 200
- 注册临时测试用户：HTTP 200
- 登录并签发新 Token：HTTP 200
- 新 Token 访问 `/api/user/me`：通过
- 篡改 Token：被拒绝
- 缺少 Token：被拒绝
- 测试实例已停止，8124 无残留监听

由于轮换前没有保留可用于回放的旧 Token，未在本次记录中直接回放旧 Token；新密钥签发、签名校验、篡改拒绝和缺失凭据拒绝均已通过，旧密钥未恢复或重新写入环境。

## 4. 自动化测试

使用已加载的 `DB_*`、`TEST_DB_*` 和新 `JWT_SECRET` 执行：

```text
Tests run: 61
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## 5. 数据与泄漏复核

主库只读复核结果：

```text
projects=35, tasks=122
active_orphan_projects=0, active_orphan_tasks=0
```

隔离测试库复核结果：

```text
users=1, projects=0, tasks=0
```

该 1 个用户为 JWT 接口验证创建的临时测试用户，未写入主库。

对本阶段日志中的新 JWT、数据库密码和阿里云 Key 做精确值扫描，命中均为 0。8123、8124 端口均无残留监听。

## 6. 未处理项与下一停顿点

- 阿里云 API Key：待用户在控制台创建并安全写入新 Key 后，单独执行 B2-4 最低成本调用验证；验证成功后再停用旧 Key。
- MySQL Root 密码：保持当前值，待应用账户和 AI 凭据全部确认后单独轮换。
- Redis：按既定决定延期，不在 B2-3 处理。
