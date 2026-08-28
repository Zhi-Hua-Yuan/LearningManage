# PR4 WP4-B：统一创建与初始分配验收记录

日期：2026-08-28
基线：`develop@99d7343` + WP4-A 工作树
工作包：普通创建与 AI 创建初始分配统一

## 交付内容

- `TaskCreateRequest` 增加可选 `assigneeUserId`，旧客户端省略该字段仍可创建任务；
- 新增 `TaskAssigneePolicy`：个人项目默认项目所有者，团队项目默认未分配，显式负责人必须是有效团队成员；
- 新增 `TaskCreationService` 统一普通创建和 AI 拆解的任务写入；
- 有初始负责人时，同事务写入 `task_assignment_log` 的 `INITIAL_ASSIGN` 记录；团队任务未分配时不产生伪造日志；
- 新增有效团队成员查询，校验 `team_member` 与 `user` 的活动状态；
- 保持 V1/V2 migration 不变，未提前实现 WP4-C 的转派接口或 WP4-D 的历史查询接口。

## 变更文件

- `src/main/java/com/spt/learningmanage/model/dto/task/TaskCreateRequest.java`
- `src/main/java/com/spt/learningmanage/model/entity/TaskAssignmentLog.java`
- `src/main/java/com/spt/learningmanage/constant/TaskAssignmentActionEnum.java`
- `src/main/java/com/spt/learningmanage/mapper/TaskAssignmentLogMapper.java`
- `src/main/java/com/spt/learningmanage/mapper/TaskAssigneeQueryMapper.java`
- `src/main/java/com/spt/learningmanage/service/TaskAssigneePolicy.java`
- `src/main/java/com/spt/learningmanage/service/TaskCreationService.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskAssigneePolicyImpl.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskCreationServiceImpl.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskServiceImpl.java`
- `src/main/java/com/spt/learningmanage/service/impl/AiServiceImpl.java`
- `.github/workflows/backend-ci.yml`
- `.github/workflows/release-gate.yml`

## 验证结果

| Gate | 结果 |
|---|---|
| `mvnw.cmd -DskipTests compile` | PASS |
| WP4-B 聚焦测试 | PASS，15 tests，0 failures/errors |
| 完整 Surefire invocation | 379 tests；源码相关测试 PASS |
| 任务写入路径静态契约 | PASS，仅 `TaskCreationServiceImpl` 允许 `taskMapper.insert` |
| V1/V2 migration | 未修改 |
| 本机 MySQL 集成测试 | BLOCKED：8 个既有集成测试因 `${TEST_DB_USERNAME}` 未配置而认证失败 |

完整测试的失败均为本机数据库连接前置条件错误（`Access denied for user '${TEST_DB_USERNAME}'@'localhost'`），没有代码断言失败；CI MySQL 容器仍需执行完整回归。

## 合同状态

- `S1-A-003`：继续 `PENDING`，待 WP4-C/D 完成转派、历史查询和 MySQL 初始分配集成证据后更新；
- `S1-R-014`：继续 `OPEN`，reason 安全规则留给 WP4-C；
- `S1-R-003`：继续 `OPEN`，成员退出竞争验收留给 PR5；
- `S1-R-008`、`S1-R-012`：保持 OPEN，分别由后续工作包处理。
