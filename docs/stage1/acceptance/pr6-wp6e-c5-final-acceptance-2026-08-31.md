# PR6 WP6-E / C5 最终验收候选记录

状态：`PASS_CANDIDATE / MYSQL_CI_PENDING`

基线：`bf214ef`（C4 合并后的 develop）

日期：2026-08-31

## 验收结果摘要

| 项目 | 当前结果 |
|---|---|
| 共享 VO 私人字段 | 本地通过；JSON 不含 `reflection`、`nextPlan`、`taskIds` |
| PRIVATE/TEAM Mapper 查询 | 已补充真实 MySQL 测试，等待 CI 数据库执行 |
| 关联一致性 | 本地事务路径回归通过；MySQL SQL 核验等待 CI |
| TEAM 锁顺序 | 本地回归通过：成员行锁先于团队读取授权 |
| AI 授权 | 本地通过：越权任务在模型调用前拒绝，模型调用为 0 |
| 统计口径 | C4 统计测试已存在，等待 CI MySQL 证据 |
| 本地非 MySQL 测试 | 501 passed |
| Surefire 总计候选值 | 553 |
| Flyway | 未修改 migration；CI 待确认历史版本 2 和 checksum |

## 合同与风险

在 C5 PR CI 和合并后的 `develop` CI 均通过前，以下状态保持不变：

```text
S1-A-006 = PENDING
S1-A-008 = PENDING
S1-R-004 = OPEN
S1-R-008 = OPEN
S1-R-012 = OPEN
PR6       = IN_PROGRESS
```

CI 通过后，另行提交 PR6 合并收口记录，填入真实合并提交、CI Run ID、最终测试数量，并将上述合同和风险更新为 PASS/CLOSED。

## 证据文件

- `pr6-wp6e-c5-final-acceptance-development-record-2026-08-31.md`
- `src/test/java/com/spt/learningmanage/mapper/WeeklyReviewMapperReadPermissionMySqlTest.java`
- `src/test/java/com/spt/learningmanage/model/vo/review/WeeklyReviewSharedVOContractTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/WeeklyReviewServiceImplTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/AiServiceImplWeeklyPolishAuthorizationTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/WeeklyReviewC5AcceptanceContractTest.java`

