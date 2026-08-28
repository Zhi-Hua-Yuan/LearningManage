# PR4 WP4-C：任务负责人变更与并发安全验收记录

日期：2026-08-29
基线：`develop@99d7343` + WP4-A/WP4-B 工作树
工作包：任务负责人转派、解除分配与审计写入

## 交付内容

- 新增 `POST /api/task/assign`，由独立 `TaskAssignmentService` 承担负责人变更；
- `expectedAssigneeUserId` 必须显式提供，支持显式 `null` 表示当前应为未分配；
- 个人项目只允许项目所有者，团队项目只允许有效成员，目标成员查询使用事务内 `FOR UPDATE`；
- 使用 MySQL null-safe `<=>` 条件执行负责人 CAS 更新，失败统一返回 `OPERATION_ERROR(50001)`；
- `ASSIGN`、`REASSIGN`、`UNASSIGN` 动作映射固定；负责人未变化时返回成功且不写日志；
- 任务更新与 `task_assignment_log` 写入处于同一事务，日志失败会回滚业务操作；
- reason 规范化（trim、200 字符上限、拒绝控制字符），不进入应用日志；
- V1/V2 migration 未修改，未提前实现 WP4-D 的历史查询接口或 PR5 成员退出处理。

## 变更文件

- `src/main/java/com/spt/learningmanage/controller/TaskController.java`
- `src/main/java/com/spt/learningmanage/model/dto/task/TaskAssignRequest.java`
- `src/main/java/com/spt/learningmanage/model/vo/task/TaskAssignVO.java`
- `src/main/java/com/spt/learningmanage/service/TaskAssignmentService.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskAssignmentServiceImpl.java`
- `src/main/java/com/spt/learningmanage/service/TaskAssigneePolicy.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskAssigneePolicyImpl.java`
- `src/main/java/com/spt/learningmanage/mapper/TaskMapper.java`
- `src/main/java/com/spt/learningmanage/mapper/TaskAssigneeQueryMapper.java`
- `src/main/java/com/spt/learningmanage/constant/TaskAssignmentActionEnum.java`
- `src/test/java/com/spt/learningmanage/constant/TaskAssignmentActionEnumTest.java`
- `src/test/java/com/spt/learningmanage/model/dto/task/TaskAssignRequestTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssignmentServiceImplTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssignmentServiceMySqlTest.java`

## 验证结果

| Gate | 结果 |
|---|---|
| `mvnw.cmd -DskipTests compile` | PASS |
| WP4-C + WP4-B 聚焦测试 | PASS，20 tests，0 failures/errors |
| 完整 Surefire invocation | 390 tests；源码相关测试 PASS；9 个 MySQL 集成测试在连接阶段阻塞 |
| CI YAML 静态门禁复核 | PASS，`FlywayCiScriptStaticTest` 11/11 |
| 本机 MySQL 集成前置 | BLOCKED：8 个既有权限测试及 1 个 WP4-C 集成测试因 `${TEST_DB_USERNAME}` 未配置而认证失败 |
| expected 字段缺失/null 区分 | PASS |
| no-op 不写日志、动作映射、reason 校验、日志失败错误路径 | PASS |
| V1/V2 migration | 未修改 |
| MySQL CAS 双并发与真实日志对账 | 待 CI/隔离 MySQL 环境执行 |

## 合同状态

- `S1-A-003`：继续 `PENDING`，等待隔离 MySQL 集成与并发对账证据，并由 WP4-D 补齐历史查询验收；
- `S1-R-014`：继续 `OPEN`，reason 规则已落地，历史查询授权与隐私展示留给 WP4-D；
- `S1-R-003`：继续 `OPEN`，成员退出/移除与负责人竞争由 PR5 处理；
- `S1-R-008`、`S1-R-012`：保持 `OPEN`，由后续工作包处理。
