# PR5 WP5-A：成员关系终止合同与并发设计验收

日期：2026-08-29

状态：`ACCEPTED（WP5-A 设计包）`

## 1. 交付范围

本工作包完成：

- [PR5 团队成员关系终止合同](../api/pr5-team-membership-termination-contract.md)；
- [ADR-005 成员关系终止与任务分配并发协议](../architecture/ADR-005-membership-termination-concurrency.md)；
- API、权限、清理范围、审计字段、锁顺序和 completed→TODO 规则冻结；
- WP5-B～WP5-F 的交接边界定义。

本工作包未完成业务写入，未新增 Java、SQL、前端或数据库迁移。

## 2. 验收清单

| 项目 | 结果 | 证据 |
|---|---|---|
| leave/remove 路由与请求响应冻结 | PASS | PR5 API 合同第 2 节 |
| 权限矩阵逐场景映射 | PASS | PR5 API 合同第 3 节 |
| 状态 0 清理包含逻辑删除/归档项目 | PASS | PR5 API 合同第 5 节 |
| 状态 1/2/3 保留历史受理人 | PASS | PR5 API 合同第 4 节 |
| 统一锁顺序冻结 | PASS | ADR-005 第 1～4 节 |
| completed→TODO 失效受理人保护 | PASS | PR5 API 合同第 7 节 |
| 终止日志字段和数量对账 | PASS | PR5 API 合同第 4、6 节 |
| V1/V2 不可变边界 | PASS | 本工作包无迁移/生产代码改动 |

## 3. 状态保持

- `S1-A-004` 仍为 `PENDING`，等待真实事务和并发测试；
- `S1-R-003` 仍为 `OPEN`，等待 WP5-D/E 证据关闭；
- V1/V2 checksum、Flyway history 和现有 432 项基线测试不在本工作包中改变。

## 4. 后续准入

WP5-B 开始前必须以本合同和 ADR-005 为唯一实现输入；任何改变 API、清理范围、锁顺序或重新打开规则的提案，必须先更新 ADR 和本合同并重新评审。
