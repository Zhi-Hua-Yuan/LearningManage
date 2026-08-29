# PR5 WP5-D1：负责人资格锁协议开发记录

日期：2026-08-29（Asia/Shanghai）
状态：`DEVELOPMENT_ONLY / ACCEPTANCE_PENDING`

## 1. 本次开发范围

D1 仅收紧负责人资格校验，不包含 completed → TODO 重新打开、成员终止、前端、Flyway、AI/RAG 或 WP5-E/F。

- 删除初始负责人校验的无锁 `COUNT` 查询；
- 初始分配与普通分配/转派统一调用 `team_member` 行锁查询（`FOR UPDATE`）；
- 在锁定查询返回后再次确认返回的成员 ID 与请求目标一致；
- 保持既有事务入口和 `team_member → task → task_assignment_log` 锁顺序不变。

## 2. 变更文件

- `src/main/java/com/spt/learningmanage/mapper/TaskAssigneeQueryMapper.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskAssigneePolicyImpl.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssigneePolicyImplTest.java`
- `src/test/java/com/spt/learningmanage/mapper/TaskAssigneeLockProtocolContractTest.java`

## 3. 开发期测试证据

执行：

```text
.\mvnw.cmd test "-Dtest=TaskAssigneePolicyImplTest,TaskCreationServiceImplTest,TaskAssignmentServiceImplTest,TaskAssigneeLockProtocolContractTest,TaskCreationWritePathContractTest"
```

结果：`22 tests / 0 failures / 0 errors`，Maven `BUILD SUCCESS`。

覆盖内容：

- 团队任务初始负责人有效、失效和返回错位成员 ID 的拒绝；
- 普通分配目标使用锁查询，并拒绝返回错位成员 ID；
- Mapper SQL 包含 `FROM team_member`、有效成员条件和 `FOR UPDATE`；
- Mapper 不再暴露无锁 `countActiveTeamAssignee`；
- 创建和分配事务入口继续声明 `rollbackFor = Exception.class`；
- 任务创建与分配既有回归路径保持通过。

## 4. 尚未完成的验收项

本记录不是 WP5-D 或 PR5 验收结论。以下事项仍需后置完成：

- WP5-B 真实 MySQL 锁等待/锁覆盖测试；
- WP5-C 真实事务回滚测试；
- WP5-D 真实初始分配、普通转派与 completed → TODO 竞争测试；
- WP5-E/F 对账、最终并发门禁及 PR5 合并验收。

当前准入判断：`WP5-D development allowed，acceptance pending；PR5 in_progress`。
