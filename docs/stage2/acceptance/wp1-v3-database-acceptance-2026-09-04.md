# WP1：V3 数据库迁移与草稿幂等预审验收记录

状态：`LOCAL_PASS / CI_PENDING`
日期：2026-09-04
阶段状态：`FROZEN`

## 1. 结论

WP1 的数据库能力已在隔离的 MySQL 8.0.41 实例上完成首轮本地验证。V3 可从空库安装，也可在固定到 V2 的历史数据副本上单独升级；等价重复可追溯归档，冲突和完整性异常在持久化 DDL 前阻断，`(user_id,draft_id)` 唯一约束生效。未迁移主库或生产库，未改变现有 API 和运行时 AI 行为。受保护 PR 的 Linux CI 通过并将证据绑定到候选提交前，WP1 不标记为最终 PASS。

## 2. 验收结果

| 检查项 | 结果 | 实测结果 |
|---|---|---|
| 分支基线 | PASS | WP1 开始时的 source baseline 与本地 `develop` 为 `4d078f6`；阶段 1 候选 `5057158` 为祖先；最终候选提交仍待 PR 绑定 |
| 空库 Flyway | PASS | 依次执行 V1、V2、V3，`migrationsExecuted=3`，current=3 |
| V2→V3 Flyway | PASS | V2 target 后导入历史 AI 数据，V3 单独执行 `migrationsExecuted=1` |
| V2 数据对账 | PASS | 迁移后业务表 23、含 history 表 24、业务行 32、14 项 V3 校验零违反 |
| 阻断型数据与 Schema 漂移 | PASS | 8 类数据异常与 5 类 Schema 前置异常均被 V3 自身阻断；失败前后持久化 V3 对象数量一致 |
| 等价重复归档 | PASS | 2 组（含 `business_id=NULL`）归档 2 条、保留 2 条；归档/删除数量相等 |
| 唯一性 | PASS | 同草稿相同或不同 operationId 均拒绝第二条；不同用户和不同草稿允许 |
| 恢复演练 | PASS | 同时生成完整备份和结构备份；V2 完整备份恢复后 22 张表、32 行对账一致，V3 字段/归档表不存在，旧唯一索引恢复 |
| V3 应用回归 | PASS | 576 tests，Failures 0，Errors 0，Skipped 0 |
| 迁移不可变 | PASS | V1/V2 哈希保持清单值；V3 已登记到发布迁移清单 |
| 生产执行策略 | PASS | `policy.v3MigrationExecuted=false`，仅隔离库执行 |

## 3. 固定证据

```text
V1 SHA-256  E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
V2 SHA-256  B40BD46F7CB303F8ED5B79AC86F78AE9078E78F8F3C26C91AAFA89F758683FE1
V3 SHA-256  A626B41B40EFB8EDC2D72F57454A738B6196A11DEA9C6E14070F07E6CFAC4177
Backend tests 576/576
Negative preflight cases 13/13
V3 reusable post-verify checks 14/14
V3 legacy backfill checks 5/5
```

本机 Docker Desktop 未运行，因此本次本地实测使用独立端口的原生 MySQL 8.0.41。GitHub Actions 已接入相同空库、存量库、13 类负例、归档与双备份恢复脚本；受保护 PR 合并前仍需以 Linux Runner 结果作为最终跨环境证据。

## 4. 状态变更

- `S2-A-005`：保持 `PENDING`；本地通过、候选提交绑定和受保护 PR Linux CI 三项齐全后再更新为 `PASS`。
- `S2-R-001`：`OPEN → CLOSED`。
- `S2-R-004`：继续 `OPEN` 到 WP5；WP1 只关闭数据库唯一约束部分。
- 阶段 2：继续 `FROZEN`，不得标记为整体 `PASS`。
- 下一工作包：WP2，仅实现 `AiModelClient.chat(...)`、消息/Tool Calling/Usage 协议与供应商兼容层，不修改已冻结的 V3。
