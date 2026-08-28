# PR4-D2-C 负责人历史查询 Controller/API 验收记录

日期：2026-08-29  
状态：`IMPLEMENTED / LOCAL_GATE_PASS / CI_PENDING`

## 1. 范围

D2-C 在 D1 冻结 API 契约和 D2-A/D2-B 查询链路基础上，完成负责人历史查询的
HTTP 适配层、JWT 登录链、统一响应、错误兼容和隐私序列化验证。

本工作包不修改 Mapper、Service 业务规则或 V1/V2 migration，不包含 MySQL 并发、
事务回滚或审计对账；后述数据库闭环继续由 D2-E 完成。

## 2. 交付内容

- 新增 `GET /api/task/{taskId}/assignment-history`；
- 路径参数映射为 D1 冻结的 `taskId`；
- `current` 默认 `1`，`size` 默认 `50`；
- Controller 只构造 `TaskAssignmentHistoryQueryRequest` 并委托
  `TaskAssignmentService.listAssignmentHistory`；
- 返回 `BaseResponse<Page<TaskAssignmentHistoryVO>>`；
- 使用真实 Spring MVC、`LoginInterceptor`、`AiRateLimitInterceptor`、
  `GlobalExceptionHandler` 和 Jackson 消息转换器执行 HTTP 契约测试；
- 验证未登录、非法 Token、权限拒绝、业务参数错误和类型绑定错误；
- 验证历史记录与用户摘要字段白名单、未分配用户和已删除用户的 null 语义；
- 验证请求完成后 `UserHolder` 被清理。

## 3. 变更文件

- `src/main/java/com/spt/learningmanage/controller/TaskController.java`
- `src/test/java/com/spt/learningmanage/controller/TaskAssignmentHistoryControllerTest.java`
- `.github/workflows/backend-ci.yml`
- `.github/workflows/release-gate.yml`
- `docs/stage1/README.md`
- 本验收记录

## 4. 本地验证证据

| Gate | 结果 | 证据 |
|---|---|---|
| Java 编译和测试编译 | PASS | D2-C 聚焦测试完成 Maven compile/testCompile |
| D2-C MVC 契约测试 | PASS | 10/10 tests passed |
| D1/D2-A/D2-B/WP4-C 聚焦回归 | PASS | 40/40 tests passed |
| 外部路由 | PASS | `/api` context path + `GET /task/{taskId}/assignment-history` |
| 默认分页 | PASS | `current=1`、`size=50` |
| 登录链 | PASS | 无 Token/非法 Token 均为 `40100`，Service 未调用 |
| 权限拒绝 | PASS | HTTP 200、业务码 `40300`、固定消息、`data=null` |
| 参数错误兼容 | PASS | 业务校验 HTTP 200；类型绑定 HTTP 400；均为 `40000` |
| 隐私字段白名单 | PASS | 历史记录 8 字段；用户摘要仅 `userId/username` |
| null 语义 | PASS | 未分配整体 null；删除用户保留 ID、username 为 null |
| 完整 Surefire | LOCAL_ENV_BLOCKED | 428 tests；414 通过，14 个 MySQL 测试因本机凭据阻塞 |
| CI expected count | UPDATED | `418 -> 428`，backend/release gate 同步 |
| CI YAML 与 migration 静态 Gate | PASS | 15/15 tests passed |
| V1/V2 migration | PASS | 未修改，published migration manifest 校验通过 |

聚焦测试：

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

D1/D2-A/D2-B/WP4-C 聚焦回归：

```text
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

完整 Surefire 本机结果：

```text
Tests run: 428, Failures: 0, Errors: 14, Skipped: 0
```

14 个错误均发生在既有 MySQL 集成测试建立事务阶段，原始原因是本机未配置
`${TEST_DB_USERNAME}`，与 D2-C 的 Controller/API 实现无关。D2-C 新增测试不连接数据库，
全部通过。完整 Gate 等待 CI 隔离 MySQL 环境验证，当前不声明 CI PASS。

## 5. 安全证据链

D2-C 证明 HTTP 请求只委托 D2-B 的 `listAssignmentHistory`。D2-B 已证明该方法在历史
Mapper 前执行 `requireTaskAssignmentHistoryView`，且权限拒绝时 Mapper 不调用；D2-C
进一步证明拒绝响应只包含固定错误码、固定消息和空 data，不返回 reason、用户摘要、
历史条数或资源存在性信息。

`S1-R-014` 的关闭还需要本分支 CI 全绿，并同时引用 WP4-C reason 输入规则、D2-B
权限前置测试和本记录的 HTTP 隐私测试；在 CI 证据产生前继续保持 OPEN。

## 6. 合同状态

- `S1-A-003`：保持 `PENDING`，等待 D2-E 并发、事务和审计对账；
- `S1-R-014`：保持 `OPEN`，D2-C CI 全绿后按完整安全证据链关闭；
- `S1-R-003`：保持 `OPEN`，由 PR5 处理成员退出/移除并发语义；
- `S1-R-008`、`S1-R-012`：保持 `OPEN`，由 PR6 处理；
- V1/V2 migration：未修改。

## 7. 后续入口

推送 D2-C 分支并取得 Backend CI 全绿证据后，更新本记录的 CI Run 和风险状态；随后
进入 D2-E，完成双并发 CAS、真实事务回滚和任务负责人/审计日志账实对账。
