# 阶段 0.3-B2-4 阿里云 API Key 切换验证记录

日期：2026-08-19
范围：本机真实 Windows 用户环境及隔离测试库
结论：通过（新 Key 验证通过，旧 Key 已停用）

## 1. 执行边界

本次只验证新阿里云 API Key 的有效性，不修改主库业务数据，不处理云服务器、Redis、Qdrant 或 MySQL Root 密码。所有注册和 AI 调用均在隔离测试库实例完成。

新 Key 由用户在阿里云控制台创建，并写入当前 Windows 用户级环境变量 `ALIYUN_API_KEY`。Key 内容未写入仓库、文档、终端输出或日志；旧 Key 在验证期间保持有效，以保留回滚能力。

## 2. 启动与环境处理

首次启动探针发现当前隔离进程未自动继承 Windows 用户级环境变量，实例在数据库认证阶段停止，未执行 AI 调用。随后从用户范围读取变量并显式注入验证进程，重新启动成功；该过程未修改主库。

## 3. 最低成本验证

验证实例使用 `test` profile 和端口 8124：

- `GET /api/health`：HTTP 200，业务 code=0
- 注册临时测试用户：业务 code=0
- 登录并签发 Token：业务 code=0
- 调用最小规模 AI 预览接口：HTTP 200，业务 code=0
- AI 调用日志与草稿均写入隔离测试库
- 验证进程已停止，8124 无残留监听

隔离测试库复核结果：

```text
users=2
ai_call_log_rows=1
ai_draft_rows=1
```

其中新增用户、调用日志和草稿均属于本次隔离验证数据，不属于主库。

## 4. 主库与日志复核

主库只读复核结果：

```text
projects=35, tasks=122
active_orphan_projects=0, active_orphan_tasks=0
```

验证实例日志精确值扫描结果：

```text
ALIYUN_API_KEY=not_found
JWT_SECRET=not_found
DB_PASSWORD=not_found
TEST_DB_PASSWORD=not_found
full_bearer_token=not_found
```

8123、8124 均无残留监听。未发现完整连接字符串、密码、JWT 或阿里云 Key 泄漏。

## 5. 自动化测试

在加载 Windows 用户级环境变量后重新执行：

```text
Tests run: 61
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

测试输出中的 WARN 为既有测试对异常分支的预期覆盖，不影响测试结果。

## 6. 后续停顿点

- 新 Key 已完成最低成本认证验证。
- 用户已在阿里云控制台停用旧 Key。
- 旧 Key 停用后使用最新源码配置重新启动隔离实例，健康检查、注册、登录和最低成本 AI 调用再次通过。
- 最终日志扫描未发现 Key、JWT、数据库密码或完整 Token；8123、8124 均无残留监听。
- B2-4 已关闭，可进入后续 MySQL Root 密码轮换步骤。
- MySQL Root 密码仍待后续独立步骤轮换；Redis 按计划延期。
