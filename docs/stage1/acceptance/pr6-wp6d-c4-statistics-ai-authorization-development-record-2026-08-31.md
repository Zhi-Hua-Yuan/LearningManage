# PR6 WP6-D / C4：统计执行人口径与 AI 授权开发记录

状态：`IMPLEMENTED / LOCAL_UNIT_PASS / MYSQL_CI_PENDING`

日期：2026-08-31

## 1. 实现范围

- 周复盘完成数和重点项目改为按 `task.assignee_user_id` 统计。
- 周统计使用 `[startDateTime, endDateTimeExclusive)` 左闭右开时间区间。
- 重点项目按完成数降序、`project_id` 升序稳定选择，并排除逻辑删除任务和项目。
- 当前周返回重点项目 ID 与名称的一致结果；不可读项目不返回名称。
- 周复盘保存时写入实际完成任务快照，不再固定写入 `0`。
- 每日复盘改名、今日任务排序的自动候选改为执行人语义，并批量过滤当前不可读任务。
- 显式任务 ID 在授权后必须全部满足日期/状态条件，禁止部分放行。
- 周复盘润色草稿确认重新校验 Payload 中的任务读取权限和周复盘更新权限，并使用行锁读取目标复盘。
- 周复盘润色项目名称通过 `PermissionService.resolveProjectScopes` 解析，不再限制为项目创建人。
- 未修改 Flyway、Prompt、模型协议和 `AiServiceImpl` 的整体结构。

## 2. 主要变更文件

- `src/main/java/com/spt/learningmanage/mapper/TaskMapper.java`
- `src/main/resources/mapper/TaskMapper.xml`
- `src/main/java/com/spt/learningmanage/model/query/review/WeeklyReviewFocusProjectRow.java`
- `src/main/java/com/spt/learningmanage/service/impl/WeeklyReviewServiceImpl.java`
- `src/main/java/com/spt/learningmanage/service/impl/AiServiceImpl.java`
- `src/main/resources/mapper/WeeklyReviewMapper.xml`

## 3. 回归测试

新增/扩展用例覆盖：

- `WeeklyReviewServiceImplTest`：执行人口径、重点项目 ID/名称一致性。
- `AiServiceImplTodayOrderTest`：自动候选使用执行人、显式任务集合整体拒绝。
- `AiServiceImplDailyReviewRenameTest`：自动候选使用执行人。
- `AiServiceImplWeeklyPolishAuthorizationTest`：草稿确认时任务权限和复盘更新权限重新校验，拒绝时不写入。
- `TaskMapperStatisticsContractTest`：Mapper SQL 合同。
- `WeeklyReviewStatisticsMapperMySqlTest`：完成数、时间区间、删除过滤、重点项目稳定排序。

## 4. 本地验证

- 编译：通过。
- 聚焦 C4 单元测试：通过，31 tests。
- 排除 MySQL 集成测试的完整本地测试：通过，495 tests。
- MySQL 集成测试：本机未配置 `TEST_DB_USERNAME/TEST_DB_PASSWORD`，连接被拒绝；必须由 CI 隔离数据库执行。

## 5. CI 测试门禁

C4 新增 9 个测试，CI 基线 `537` 更新为 `546`，已同步：

- `.github/workflows/backend-ci.yml`
- `.github/workflows/release-gate.yml`

最终测试数量和 MySQL 证据以 CI Run 为准。

## 6. 验收结论

- 统计和 AI 授权代码已完成。
- 本地非数据库测试已通过。
- C4 在 CI MySQL 集成测试、完整测试门禁和代码评审通过后，才可标记为 `COMPLETED / CI_PASS`，并据此更新 PR6 风险和 `S1-A-008` 状态。
