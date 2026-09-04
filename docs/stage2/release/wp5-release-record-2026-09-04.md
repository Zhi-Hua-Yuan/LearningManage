# WP5 Release 记录

状态：`PASS`

## 发布绑定

| 项目 | 值 |
|---|---|
| 工作包 | WP5 AI 草稿生命周期与写入安全收口 |
| 实现 PR | [#114](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/114) |
| 候选实现提交 | `80f357b357bf98e18f4bdd1e53392aede002e8cf` |
| 合并后 Backend CI | [run 33888407204](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33888407204) |
| 跨仓候选门禁 | [run 33889047346](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33889047346) |
| 前端候选提交 | `2ef907f292fbbacecf8a68f7d24c4701a555aa8a` |
| Tag | `stage2-wp5-v1.0.0` |
| Release | [stage2-wp5-v1.0.0](https://github.com/Zhi-Hua-Yuan/LearningManage/releases/tag/stage2-wp5-v1.0.0) |

## 门禁结果

- 跨仓候选 10/10 Job 通过。
- 后端 674/674、前端 459/459 通过。
- 隔离 MySQL 20 路草稿并发确认只产生一次正式业务写入。
- 清单重排终态竞争与快照失效原子回滚通过。
- Flyway 空库、存量库与 V1/V2/V3 不可变检查通过，未新增 V4。
- 运行时 API 与前端合同 44/44 匹配，缺失 0；legacy 37 保持兼容。
- Docker Stub 的预览、取消、确认和幂等重放闭环通过。

## 证据摘要

- Candidate Manifest SHA-256：`78BD22DCEF2E60CC8B1EAC8355288E8BDD0826A6A1862BC8FB1184094BC1C98C`
- API 比对报告 SHA-256：`5234569B647EDB5C74D321686E01D22D574EA0C4E5412D3BB3808C8A84B0B300`
- 全栈 AI 证据 SHA-256：`84C0F5EFA21AC3D9368ABC4CB4639242B2A3860129E53C6268F7541717B74C5B`

## 范围说明

本 Release 仅封存阶段 2 的 WP5，关闭 `S2-A-009` 和 `S2-R-004`，不代表阶段 2 整体完成。`S2-A-010`～`S2-A-012` 以及 WP6～WP8 继续按阶段合同实施。
