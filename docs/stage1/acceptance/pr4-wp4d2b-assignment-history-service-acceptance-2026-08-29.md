# PR4-D2-B 负责人历史查询 Service 验收记录

日期：2026-08-29
状态：`IMPLEMENTED / LOCAL_UNIT_GATE_PASS / CONTROLLER_PENDING`

## 1. 范围

D2-B 在 D1 冻结模型和 D2-A 分页 Mapper 的基础上，完成负责人历史查询
Service、参数校验、`TASK_ASSIGNMENT_HISTORY_VIEW` 权限前置和最小 VO 映射。
本工作包未实现 Controller、并发验收或审计对账，未修改 V1/V2 migration。

## 2. 交付内容

- `TaskAssignmentService` 增加 `listAssignmentHistory` 查询入口；
- 登录状态校验和 `taskId/current/size` 边界校验；
- Mapper 查询前执行 `requireTaskAssignmentHistoryView`；
- 使用 `Page<TaskAssignmentHistoryVO>` 返回 D1 冻结分页结构；
- action 严格限制为六种冻结枚举，未知值 fail-closed；
- 未分配用户返回整体 `null`；
- 已删除或无法关联用户保留 `userId`、将 `username` 返回为 `null`；
- 只映射 D1 白名单字段，不暴露账户、角色、删除标志或任务私有字段；
- Mapper 空结果和跨任务 Row 采用系统错误拒绝，避免静默返回损坏审计数据。

## 3. 变更文件

- `src/main/java/com/spt/learningmanage/service/TaskAssignmentService.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskAssignmentServiceImpl.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssignmentHistoryServiceTest.java`

## 4. 验证证据

| Gate | 结果 | 证据 |
|---|---|---|
| Java 编译 | PASS | `mvnw.cmd test -Dtest=TaskAssignmentHistoryServiceTest` |
| D2-B Service 单元测试 | PASS | 14/14 tests passed |
| D1/D2-A/WP4-C 回归 | PASS | 聚焦回归 29/29 tests passed |
| 完整 Surefire | PENDING | 418 tests；404 个源码相关测试通过，14 个 MySQL 集成测试因本机 `${TEST_DB_USERNAME}` 凭据阻塞 |
| 权限前置 | PASS | 权限失败时验证历史 Mapper 未调用 |
| 分页元数据 | PASS | current、size、total、pages 断言通过 |
| null/删除用户语义 | PASS | 未分配整体 null、删除用户保留 ID 断言通过 |
| action 白名单 | PASS | 未知 action fail-closed 断言通过 |
| `git diff --check` | PASS | 无 whitespace error |
| Controller/API | PENDING | 由 D2-C 完成 |
| MySQL 并发/事务/审计对账 | PENDING | 由 D2-E 完成 |

本机聚焦命令结果：

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

完整 Surefire 本机结果：

```text
Tests run: 418, Failures: 0, Errors: 14
```

14 个错误均发生在既有或 D2-A MySQL 集成测试建立事务阶段，原始原因是本机未配置
`${TEST_DB_USERNAME}`；D2-B 新增的 14 个 Service 单元测试不依赖数据库并全部通过。

## 5. 合同状态

- `S1-A-003`：保持 `PENDING`，等待 D2-C 接口、D2-E 并发和审计对账；
- `S1-R-014`：保持 `OPEN`，等待接口授权和隐私泄露测试；
- `S1-R-003`：保持 `OPEN`，由 PR5 处理成员退出/移除并发语义；
- V1/V2 migration：未修改。
