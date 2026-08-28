# PR4-D2-A 负责人历史分页 Mapper 验收记录

日期：2026-08-29
状态：`IMPLEMENTED / MYSQL_GATE_PENDING`

## 1. 范围

D2-A 完成负责人历史查询的 Mapper 与分页 SQL，实现 D1 冻结的
`TaskAssignmentHistoryRow` 查询契约。未实现 Service、Controller、权限执行、并发验收或审计对账；未修改 V1/V2 migration。

## 2. 交付内容

- `TaskAssignmentLogMapper` 增加 `selectAssignmentHistoryPage` 分页查询方法；
- 新增 `TaskAssignmentLogMapper.xml`，使用显式 `resultMap` 映射全部 D1 Row 字段；
- 使用一次日志查询和三次 `LEFT JOIN user` 返回原负责人、新负责人、操作人当前展示名；
- 已删除或不存在用户保留日志中的用户 ID，展示名解析为 `null`；
- 未分配用户通过日志字段的 `null` 保持空语义；
- 固定 `create_time DESC, id DESC` 稳定排序；
- 新增静态 XML 契约测试和 V2 MySQL 集成测试夹具；
- 未使用 `SELECT *`、动态排序、字符串插值或逐条用户查询；
- V1/V2 migration 文件未修改。

## 3. 验证证据

| Gate | 结果 | 证据 |
|---|---|---|
| Java 编译 | PASS | `mvnw.cmd test -Dtest=TaskAssignmentLogMapperContractTest` 完成编译 |
| Mapper XML 静态契约 | PASS | 4/4 tests passed |
| MySQL 分页/排序/隔离/删除用户语义 | PENDING | 5 tests 在事务建立阶段被本机 `${TEST_DB_USERNAME}` 凭据阻塞 |
| 完整 Surefire | PENDING | 404 tests：非数据库测试通过；14 个 MySQL 集成测试错误（其中 D2-A 新增 5 个） |
| `git diff --check` | PASS | 无 whitespace error |
| V1/V2 migration 不变 | PASS | 本工作包未修改 migration |

MySQL 阻塞原始错误：

```text
Access denied for user '${TEST_DB_USERNAME}'@'localhost' (using password: YES)
```

该阻塞与此前 D1/WP4-C 的本机环境状态一致，不代表 SQL 或映射断言失败。CI/隔离数据库环境仍必须执行并记录 5 个 MySQL 测试的真实结果后，D2-A 才能升级为 `PASS`。完整测试门槛已按 Surefire 实际总数更新为 `404`。

## 4. 测试数据

新增 `task_assignment_history_mapper_v2_seed.sql`，复用现有
`permission_mapper_v2_seed.sql` 的用户、项目和任务数据，仅补充确定性日志行，覆盖：

- 相同时间戳的 ID 二级排序；
- 初始分配、重分配、解除分配；
- 已删除用户；
- 不存在用户和操作者；
- `null` 负责人和 `null` reason；
- 跨任务隔离和空结果页。

## 5. 后续收口条件

配置 `TEST_DB_USERNAME`、`TEST_DB_PASSWORD` 后执行：

```text
./mvnw test -Dtest=TaskAssignmentLogMapperMySqlTest
```

通过后再将 D2-A 状态更新为 `PASS`，并由后续 D2-B/D2-C 继续完成 Service、权限、接口和 VO 映射。
