# PR4 WP4-D1：负责人历史查询契约验收记录

日期：2026-08-29
基线：`develop@99d7343` + WP4-A/WP4-B/WP4-C 工作树
工作包：历史查询契约与模型冻结

## 交付内容

- 新增 `TaskAssignmentHistoryQueryRequest`，冻结 `current=1`、`size=50` 默认值；
- 新增扁平查询行 `TaskAssignmentHistoryRow`；
- 新增最小用户摘要 `AssignmentUserSummaryVO` 与历史视图 `TaskAssignmentHistoryVO`；
- 明确未分配、用户删除、展示名和 reason 的 API 语义；
- 增加 DTO 默认值、VO 字段白名单、序列化和 Row/VO 隔离测试；
- V1/V2 migration、WP4-C 负责人变更接口保持不变。

## 变更文件

- `src/main/java/com/spt/learningmanage/model/dto/task/TaskAssignmentHistoryQueryRequest.java`
- `src/main/java/com/spt/learningmanage/model/query/task/TaskAssignmentHistoryRow.java`
- `src/main/java/com/spt/learningmanage/model/vo/task/AssignmentUserSummaryVO.java`
- `src/main/java/com/spt/learningmanage/model/vo/task/TaskAssignmentHistoryVO.java`
- `src/test/java/com/spt/learningmanage/model/dto/task/TaskAssignmentHistoryQueryRequestTest.java`
- `src/test/java/com/spt/learningmanage/model/vo/task/TaskAssignmentHistoryContractTest.java`
- `src/test/java/com/spt/learningmanage/model/query/task/TaskAssignmentHistoryRowContractTest.java`
- `docs/stage1/api/pr4-assignment-history-contract.md`

## 验收结果

| Gate | 结果 |
|---|---|
| D1 契约模型与字段白名单 | PASS，5 tests，0 failures/errors |
| 编译 | PASS，`mvnw.cmd -DskipTests compile` |
| 完整 Surefire invocation | 395 tests；源码相关测试 PASS；9 个 MySQL 集成测试因 `${TEST_DB_USERNAME}` 未配置在连接阶段阻塞 |
| V1/V2 migration 未修改 | PASS |
| D2 Mapper/Service/Controller | 未开始，按范围排除 |
| S1-A-003 | `PENDING` |
| S1-R-014 | `OPEN` |

## 后续入口

D2 读取本契约实现单次查询、`TASK_ASSIGNMENT_HISTORY_VIEW` 授权、稳定分页和 MySQL 集成验收；不得扩展本 D1 VO 字段或改变 null 语义。
