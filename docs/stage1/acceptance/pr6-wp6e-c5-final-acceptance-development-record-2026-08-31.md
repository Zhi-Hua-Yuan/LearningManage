# PR6 WP6-E / C5：最终验收开发记录

状态：`IMPLEMENTED / LOCAL_UNIT_PASS / MYSQL_CI_PENDING`

日期：2026-08-31

基线提交：`bf214ef`（C4 合并后的 `develop`）

## 1. 验收范围

C5 不增加新的业务协议，针对 WP6-B～WP6-D 已实现的周复盘隐私、关联、历史读取、统计和 AI 授权能力补充最终验收证据：

- PRIVATE/TEAM 多用户读取边界和共享 VO 序列化；
- 周复盘任务关联的一致性、重复约束和孤儿记录核验；
- TEAM 成员锁与写入授权顺序；
- 统计按任务执行人、时间窗口和删除状态核验；
- AI 显式资源鉴权失败时零模型调用、零业务写入；
- Flyway V1/V2 不变更、完整 Maven 测试数量门禁保持真实值。

## 2. 本次测试证据

- `WeeklyReviewSharedVOContractTest`：团队共享 JSON 不包含反思、下一步计划和任务关联等私人字段；
- `WeeklyReviewMapperReadPermissionMySqlTest`：团队共享查询排除 PRIVATE 记录，并执行 visibility、孤儿关联和重复关联 SQL 核验；
- `WeeklyReviewServiceImplTest`：TEAM 成员行锁先于团队读取授权，关联替换部分失败时拒绝完成；
- `AiServiceImplWeeklyPolishAuthorizationTest`：显式任务授权失败时不调用模型、不写入草稿或复盘；
- `WeeklyReviewStatisticsMapperMySqlTest`：真实 MySQL 统计口径、边界和稳定排序；
- `WeeklyReviewTaskMapperMySqlTest`：批量关联、删除和唯一约束。

本地非 MySQL 测试：`501` 项通过；PR #63 的 CI 权威 Surefire 计数为 `554`。本机 MySQL 使用占位账号，连接被拒绝，未将该结果伪装为集成测试通过。

## 3. 验收门禁

以下结果必须以 CI 隔离 MySQL 运行结果为准：

| 门禁 | 目标 |
|---|---|
| 后端完整测试 | 实际 Surefire 数量与两个 workflow 的 `CI_EXPECTED_TEST_COUNT` 完全一致 |
| MySQL | 全部 Mapper/Service 集成测试通过 |
| Flyway | 成功历史版本最大值保持 `2`，V1/V2 checksum 不变 |
| 隐私 | 非作者无法从共享接口获得 PRIVATE 正文或私人字段 |
| AI 安全 | 越权、缺失、删除 ID 在模型调用前被拒绝，模型调用次数为 `0` |
| 数据一致性 | 无重复、孤儿或跨团队关联；不允许部分提交 |

当前 `S1-A-006`、`S1-A-008` 仍为 `PENDING`，待 C5 PR CI 和合并后的 `develop` CI 通过后填写真实 Run ID、测试数量和合并提交。

## 4. 不应在 C5 中完成的事项

- 不修改 V1/V2 migration；
- 不通过降低测试数量、跳过 MySQL 类或放宽权限断言来通过门禁；
- 不提前把 `S1-A-006`、`S1-A-008` 标记为 PASS；
- 不提前关闭 `S1-R-004`、`S1-R-008`、`S1-R-012`。
