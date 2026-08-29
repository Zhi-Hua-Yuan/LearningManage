# PR5 WP5-D3：completed → TODO 负责人资格保护开发记录

日期：2026-08-29（Asia/Shanghai）
状态：`DEVELOPMENT_ONLY / UNIT_AND_STATIC_PASS / MYSQL_ACCEPTANCE_PENDING`

## 1. 本次开发范围

D3 仅实现任务从完成态重新打开为 `TODO` 时的当前负责人资格保护。未扩展普通分配、团队成员终止、周复盘、前端、Flyway、AI/RAG 或 WP5-E/F。

冻结流程为：

```text
读取任务快照
→ 校验 completed → TODO
→ 团队负责人资格锁（team_member ... FOR UPDATE）
→ 负责人仍有效确认
→ 状态 + 旧负责人双条件 CAS
→ 写入状态幂等记录
```

团队任务无负责人不获取成员锁；个人项目仅允许项目所有者作为当前负责人。负责人已经失效时返回 `50001`，不执行任务 CAS，也不写幂等记录。

## 2. 变更文件

- `src/main/java/com/spt/learningmanage/service/TaskAssigneePolicy.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskAssigneePolicyImpl.java`
- `src/main/java/com/spt/learningmanage/mapper/TaskMapper.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskServiceImpl.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssigneePolicyImplTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskServiceImplTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskReopenProtectionContractTest.java`

未修改 Flyway 迁移、团队终止 Mapper/Service 或负责人分配日志路径。

## 3. 实现要点

- 新增 `TaskAssigneePolicy.validateReopenAssignee()`，复用现有带 `FOR UPDATE` 的团队成员资格查询；重新打开资格失败使用 `OPERATION_ERROR / 50001`。
- 新增 `TaskMapper.compareAndSetStatusForReopen()`，同时比较 `status` 与 `assignee_user_id`，使用 MySQL `<=>` 支持空负责人 CAS。
- `TaskServiceImpl.changeStatus()` 仅在真实完成态到 `TODO` 时走 D3 分支；其他状态迁移继续使用原路径。
- 重新打开成功后清空 `completed_at`；D3 不产生 `task_assignment_log`。
- CAS 冲突或资格失败均不会写状态幂等记录。

## 4. 开发期测试证据

执行：

```text
.\mvnw.cmd test "-Dtest=TaskAssigneePolicyImplTest,TaskServiceImplTest,TaskReopenProtectionContractTest"
```

结果：`26 tests / 0 failures / 0 errors / 0 skipped`，Maven `BUILD SUCCESS`。

该结果仅证明 Java 单元测试和静态契约测试通过，不构成真实 MySQL 锁、事务回滚或竞争验收证据。

## 5. 尚未完成的验收项

- WP5-B 真实 MySQL 锁等待和覆盖测试；
- WP5-C 真实事务回滚测试；
- WP5-D 真实分配、转派、重新打开竞争测试；
- WP5-E/F 对账、最终并发门禁及 PR5 合并验收。

当前准入判断：

```text
WP5-D1：development completed / acceptance pending
WP5-D2：development completed / acceptance pending
WP5-D3：development completed / acceptance pending
WP5-D：development completed / acceptance pending
PR5：in_progress
```
