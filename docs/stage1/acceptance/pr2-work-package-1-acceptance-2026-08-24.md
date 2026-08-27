# PR2 工作包 1 本地验收记录

状态：`PASS`

日期：2026-08-24

## 1. 验收范围

本记录验收 PR2 工作包 1“迁移输入与测试样本”。验收对象是迁移规则输入、合法与负向 fixture、expected JSON、只读 preflight SQL 以及对应静态测试，不包含正式 V2 迁移执行或生产数据库操作。

## 2. 交付矩阵

| ID | 交付物 | 状态 |
|---|---|---|
| WP1-D-001 | [迁移输入与测试样本合同](../database/pr2-work-package-1-input-and-fixture.md) | PASS |
| WP1-D-002 | [V1 到 V2 expected JSON](../../../src/test/resources/db/stage1/v1_to_v2_expected.json) | PASS |
| WP1-D-003 | [合法 V1 seed fixture](../../../src/test/resources/db/stage1/v1_to_v2_seed.sql) | PASS |
| WP1-D-004 | 三个负向 fixture | PASS |
| WP1-D-005 | [V2 preflight SQL](../../../sql/flyway/stage1/01_preflight_v2.sql) | PASS |
| WP1-D-006 | preflight 与 fixture 静态测试 | PASS |

## 3. 验收证据

| 检查项 | 结果 | 证据摘要 |
|---|---|---|
| 派生规则和生命周期覆盖 | PASS | `COALESCE(task.assignee_id, task.user_id)`、活跃/完成/逻辑删除三类任务均已固定并覆盖 |
| expected JSON 完整性 | PASS | 5 个用户任务样本、5 个初始分配日志预期、2 个 PRIVATE 周复盘；JSON 可解析且 ID/数量/派生值一致 |
| 合法 seed 结构 | PASS | 仅写入 7 张允许的 V1 业务表；行数为 5/1/3/2/2/5/2 |
| 负向 fixture | PASS | 未知角色→`V2-P-010`；孤儿受理人→`V2-P-021`；非团队成员→`V2-P-032` |
| preflight 静态合同 | PASS | 25 个 Gate 编号完整、唯一、有序；单结果集、只读、不读取敏感正文 |
| 新增静态测试 | PASS | 9 项新增测试全部通过 |
| 后端回归测试 | PASS | Maven Surefire 共 93 项测试，失败 0、错误 0、跳过 0 |
| V1 不可变性 | PASS | 既有 `FlywayV1MigrationStaticTest` 通过，未修改已发布 V1 文件 |
| 文件质量与边界 | PASS | 新增文件无尾随空白；未修改 Java 生产代码、CI、部署配置或已发布迁移 |

## 4. 工作包 1 Gate

| Gate | 结论 |
|---|---|
| WP1-G-001 迁移输入规则无歧义 | PASS |
| WP1-G-002 合法 fixture 与 expected JSON 一致 | PASS |
| WP1-G-003 负向 fixture 各自绑定明确失败 Gate | PASS |
| WP1-G-004 preflight 只读且检查目录冻结 | PASS |
| WP1-G-005 自动化测试全部通过 | PASS |
| WP1-G-006 工作区边界符合合同 | PASS |

## 5. 结论与范围声明

工作包 1 已通过本地验收，迁移输入和测试样本现已冻结，可以进入工作包 2。

本次 PASS 不表示 `V2__stage1_business_semantics_and_permissions.sql` 已实现，不表示 V2 已在数据库执行成功，也不表示阶段 1 数据库 Gate 已通过。工作包 2 必须直接引用本工作包冻结的规则、ID、预期结果和 `V2-P-*` 检查目录。

工作区原有 `deploy/docker-compose.release-gate.yml` 修改未被本工作包修改或纳入。
