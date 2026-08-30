# PR6 WP6-C2：周复盘项目/任务关联与事务开发记录

状态：`IMPLEMENTED（等待 CI 与真实 MySQL 验收）`

日期：2026-08-31

## 1. 交付范围

- 新增 `WeeklyReviewAssociationValidator`，统一执行项目/任务批量权限解析、实体存在性校验和 TEAM 归属校验；
- PRIVATE 允许作者可读的个人项目/任务及可读团队资源；TEAM 要求重点项目和全部任务均属于请求团队；
- task ID 去重并保持请求顺序，缺失、删除、越权、跨团队和非法 ID 整体拒绝；
- 重点项目名称只从数据库读取，不信任请求中的名称；
- 新增 `weekly_review` 按 ID、按作者/年份/周次的 `FOR UPDATE` 查询；
- 使用显式 `updateForWrite` 更新 SQL，确保 nullable 的团队/项目/摘要字段可以被真正清空；
- TEAM 写入先锁定活动成员关系，再锁定复盘行，复用 PR5 的成员并发闸门；
- 保存、更新、关联删除/批量插入处于同一事务；更新采用关联集合整体替换，空集合表示清空；
- 删除复盘时先删除 `weekly_review_task` 关联，再物理删除复盘主行；
- 作者详情和历史列表按一次批量关联查询返回 taskIds，团队共享 VO 仍不包含私人字段。

## 2. 关键不变量

1. 任何一个项目或任务关联校验失败，复盘主记录和关联记录均不得提交；
2. TEAM 复盘不能引用个人项目、其他团队项目或跨团队任务；
3. `weekly_review_task` 不产生重复 `(weekly_review_id, task_id)`；
4. 更新时旧关联不会残留，`null/[]` 会清空对应集合；
5. 会员退出与 TEAM 复盘写入共享活动成员行锁，不能在成员失效后继续写入 TEAM 复盘；
6. 资源校验查询按批量执行，不按单个 ID 产生 N+1 查询。

## 3. 验证结果

### 3.1 编译

```text
.\mvnw.cmd test -DskipTests
结果：PASS
```

### 3.2 C2 聚焦测试

```text
.\mvnw.cmd test '-Dtest=WeeklyReviewServiceImplTest,WeeklyReviewAssociationValidatorTest,WeeklyReviewMapperPrivacyContractTest'
结果：13/13 PASS
```

覆盖：批量校验、去重、缺失/越权/跨团队拒绝、关联保存、更新清空、删除顺序、批量插入失败和锁查询契约。

### 3.3 全量测试

本机全量测试报告为 `528` 个测试；非 MySQL 测试通过。46 个 MySQL 集成测试因本机仍使用占位凭据 `${TEST_DB_USERNAME}`，报 `Access denied`，导致其中一个 MySQL 测试类未完整计数。CI 隔离 MySQL 首轮实际计数为 `529`，因此门禁以 CI 可重复计数 `529` 为准。

已同步：

- `.github/workflows/backend-ci.yml`：`CI_EXPECTED_TEST_COUNT=529`；
- `.github/workflows/release-gate.yml`：`CI_EXPECTED_TEST_COUNT=529`。

## 4. 数据库与后续边界

- 未修改已发布 V1/V2 Flyway 文件；C2 复用 C1 已存在的 `weekly_review_task` 表和唯一键；
- C2 不改变 WP6-D 的统计执行者口径和 AI 授权逻辑；
- C2 PR 必须在 CI 完成真实 MySQL、回滚和并发证据后合并到 `develop`；
- `S1-A-006`、`S1-A-008` 仍保持 `PENDING`，直到 WP6-D/WP6-E 完成最终验收。
