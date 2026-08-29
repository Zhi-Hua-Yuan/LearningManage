# PR5 WP5-B：成员终止 Mapper 实现验收记录

日期：2026-08-29（Asia/Shanghai）
状态：`IMPLEMENTED / MYSQL_GATE_BLOCKED`

## 1. 验收对象

本工作包只实现 WP5-A 已冻结的数据库原语，不实现 Service、Controller 或业务并发流程：

1. `TeamMemberMapper`：有效成员行锁、关系 ID 升序、角色/身份/有效状态 CAS 失效；
2. `TaskMapper`：团队内未完成受理任务锁定、批量解除受理人；
3. `TaskAssignmentLogMapper`：批量写入 `MEMBER_LEFT` / `MEMBER_REMOVED` 所需的固定审计字段；
4. 静态契约测试、V2 MySQL fixture、Mapper 集成与锁等待测试。

## 2. 实现摘要

- 成员锁 SQL 使用 `is_delete = 0`、`deleted_at IS NULL`、`ORDER BY id ASC FOR UPDATE`；空集合通过 `AND 1 = 0` 安全短路。
- 成员 CAS 同时绑定关系 ID、团队 ID、用户 ID、预期角色和 active 状态。
- 任务锁 SQL 只按团队、受理人和 `status = 0` 筛选；有意不加入任务逻辑删除、项目逻辑删除或项目归档条件，以覆盖冻结的清理范围。
- 批量解除仅针对已锁定的任务 ID 集合，并写入 `assigned_by_user_id` / `assigned_at`。
- 日志批量写入只包含冻结字段，并在 Mapper 入口按 `taskId` 升序构造批量参数。由于既有历史 XML 契约要求 `TaskAssignmentLogMapper.xml` 保持只读，批量 INSERT 采用 Mapper 注解脚本实现，未改变历史查询 XML。
- 未修改 V1/V2 Flyway migration、PR4 API、Service、Controller 或运行配置。

## 3. 测试与门禁

### 已完成（静态）

- `MembershipCleanupMapperContractTest`：校验锁条件、锁顺序、清理范围、空集合短路、CAS 条件、日志字段和 fixture 非破坏性。
- Java/XML 结构复核：Mapper namespace、结果模型和旧历史查询只读契约保持不变。
- 执行证据：`.\mvnw.cmd -Dtest=MembershipCleanupMapperContractTest test`，`5 tests / 0 failures / 0 errors`；既有 Mapper 契约回归共 `12 tests / 0 failures / 0 errors`。

### 待执行（真实 MySQL）

`MembershipCleanupMapperMySqlTest` 与 `MembershipCleanupMapperLockMySqlTest` 覆盖：

- 有效成员锁定范围及关系 ID 顺序；
- 逻辑删除任务仍被清理；
- 归档项目和逻辑删除项目下任务仍被清理；
- 完成任务、跨团队任务、个人项目任务不被清理；
- 批量更新条数与日志条数一致；
- 成员锁和任务锁对 CAS/批量更新产生真实等待。

当前 `application-test.yml` 依赖 `${TEST_DB_USERNAME}`，本机未配置该凭据，无法建立真实 MySQL 连接。因此 WP5-B 暂不能声明 MySQL 验收通过或合并门禁通过；补齐隔离测试库凭据后，应先运行上述两类测试，再执行完整 Maven 回归和 CI 门禁。

本次尝试执行真实测试：`.\mvnw.cmd "-Dtest=MembershipCleanupMapperMySqlTest,MembershipCleanupMapperLockMySqlTest" test`，共 `6 tests / 6 errors`，错误均为 `Access denied for user '${TEST_DB_USERNAME}'@'localhost'`，未进入业务断言。

## 4. 结论

WP5-B 代码实现和静态合同部分已完成，范围符合 WP5-A 冻结设计；真实 MySQL 验收为唯一未完成项，阻塞状态为环境凭据而非代码结论。
