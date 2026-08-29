# PR5 WP5-D2：任务写路径锁顺序开发记录

日期：2026-08-29（Asia/Shanghai）
状态：`DEVELOPMENT_ONLY / ACCEPTANCE_PENDING`

## 1. 本次开发范围

D2 仅固化创建任务指定负责人、普通分配、转派和取消分配的调用顺序与失败语义，不包含 completed → TODO 重新打开、真实 MySQL 并发、前端、Flyway、AI/RAG 或 WP5-E/F。

本次确认现有生产实现已经遵守冻结顺序，因此没有为了制造改动而重构生产代码；通过单元测试和静态契约测试固定以下开发合同：

```text
创建并指定负责人：
team_member 资格锁/校验
→ task INSERT
→ task_assignment_log INITIAL_ASSIGN

普通分配/转派：
expectedAssigneeId 快照校验
→ team_member 资格锁/校验
→ task assignee CAS
→ task_assignment_log ASSIGN/REASSIGN

取消分配：
null 目标策略校验（不获取目标成员锁）
→ task assignee CAS
→ task_assignment_log UNASSIGN
```

## 2. 变更文件

- `src/test/java/com/spt/learningmanage/service/impl/TaskCreationServiceImplTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssignmentServiceImplTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssignmentWritePathContractTest.java`
- `docs/stage1/README.md`
- `docs/stage1/acceptance/pr5-wp5d2-task-mutation-lock-order-development-record-2026-08-29.md`

生产实现文件未修改。D2 测试确认当前 `TaskCreationServiceImpl` 和 `TaskAssignmentServiceImpl` 已符合冻结合同。

## 3. 开发期测试覆盖

### 3.1 创建任务

- 有效团队负责人：资格校验先于任务插入，任务插入先于 `INITIAL_ASSIGN` 日志；
- 团队任务不指定负责人：正常插入任务，不写初始分配日志；
- 无效团队负责人：资格校验失败后不插入任务、不写日志；
- 任务插入失败：不写初始分配日志；
- 初始分配日志失败：异常向外传播，真实事务回滚证明仍后置。

### 3.2 普通分配、转派和取消分配

- `null → member`：目标资格校验先于任务 CAS，随后写 `ASSIGN`；
- `member A → member B`：目标资格校验先于任务 CAS，随后写 `REASSIGN`；
- `member → null`：策略接收 null，任务 CAS 后写 `UNASSIGN`；
- 无效目标成员：不执行任务 CAS、不写日志；
- stale `expectedAssigneeId`：在目标资格锁之前拒绝，不执行任务 CAS、不写日志；
- no-op：仍校验当前目标成员资格，但不执行任务 CAS、不写日志；
- 任务 CAS 冲突：不写日志；
- 日志失败：异常向外传播，真实事务回滚证明仍后置。

### 3.3 静态写入口合同

- 生产任务创建继续集中在 `TaskCreationServiceImpl`；
- 生产负责人 CAS 继续集中在 `TaskAssignmentServiceImpl`；
- AI 任务拆解继续复用统一任务创建服务；
- 创建和分配入口继续声明 `@Transactional(rollbackFor = Exception.class)`；
- D1 的 `team_member ... FOR UPDATE` 与无 `COUNT` 回退合同继续通过。

## 4. 开发期测试证据

执行：

```text
.\mvnw.cmd test "-Dtest=TaskCreationServiceImplTest,TaskAssignmentServiceImplTest,TaskAssigneePolicyImplTest,TaskAssigneeLockProtocolContractTest,TaskCreationWritePathContractTest,TaskAssignmentWritePathContractTest"
```

结果：`29 tests / 0 failures / 0 errors / 0 skipped`，Maven `BUILD SUCCESS`。

该结果仅证明 Java 单元测试和静态契约测试通过，不构成真实 MySQL 锁、事务回滚或竞争验收证据。

## 5. 尚未完成的验收项

本记录不是 WP5-D 或 PR5 验收结论。以下事项仍需后置完成：

- WP5-B 真实 MySQL 锁等待和锁覆盖测试；
- WP5-C 真实事务回滚测试；
- WP5-D completed → TODO 重新打开保护开发；
- WP5-D 真实初始分配、普通转派、取消分配及重新打开竞争测试；
- WP5-E/F 对账、最终并发门禁及 PR5 合并验收。

当前准入判断：

```text
WP5-D1：development completed / acceptance pending
WP5-D2：development completed / acceptance pending
WP5-D：development in progress / acceptance pending
PR5：in_progress
```

下一开发工作包为 D3：实现 completed → TODO 的当前负责人资格保护；D2 不提前实现或验收该路径。
