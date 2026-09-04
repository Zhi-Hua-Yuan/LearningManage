# WP6 Release 记录

状态：`PASS`

## 发布绑定

| 项目 | 值 |
|---|---|
| 工作包 | WP6 AI 安全可观测、成本治理与韧性保护 |
| 实现 PR | [#116](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/116) |
| 候选实现提交 | `078d3aaf41708aac185ec05f8c264e43099ce172` |
| 跨仓候选门禁 | [run 33900744185](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33900744185) |
| 前端候选提交 | `2ef907f292fbbacecf8a68f7d24c4701a555aa8a` |
| 候选名称 | `stage2-wp6-20260905` |
| Tag | `stage2-wp6-v1.0.0` |
| Release | [stage2-wp6-v1.0.0](https://github.com/Zhi-Hua-Yuan/LearningManage/releases/tag/stage2-wp6-v1.0.0) |

## 门禁结果

- 候选 Release Gate 10/10 Job 通过。
- 后端 709/709、前端 459/459 通过。
- Flyway 空库与存量库升级通过，V1/V2/V3 未修改且未新增 V4。
- Docker 全栈运行、运行时 API 契约 44/44 匹配、legacy 37 保持，缺失 0。
- Docker AI Stub 的任务拆解草稿闭环通过。
- Gitleaks、仓库守卫、生成的 JAR/dist 产物扫描和候选 Manifest 校验全部通过。
- `S2-A-010`、`S2-A-011` 通过；`S2-R-005`、`S2-R-006`、`S2-R-007`、`S2-R-009` 关闭。

## 证据摘要

- Candidate Manifest SHA-256：`7311A0C4D2EB751DC477C29C9544192C6DC42CA90D47960AC31AE195AFD737D5`
- Backend JAR SHA-256：`6969F2DA26719B8FFCB65B275BE7553BD89FE81EA071AD2AAD8B142A1D2ECAD4`
- API 比对报告 SHA-256：`AF13A31207019571A9FCFF5C61E4FECB0195831C88A03BE8AA073A31C4CFB5DE`
- 全栈 AI 证据 SHA-256：`124A6BB2F2D12AA46A7AF360FB4C993F4B9605549CDE3A3FB5B884EC082EA78D`
- 前端合同 SHA-256：`4F8CB8D3B92252E4375B49DD102E7CDE75F819827713060D6E521BED19F0B2F6`

## 范围说明

本 Release 封存阶段 2 的 WP6，不代表阶段 2 整体完成。`S2-A-012`、`S2-R-003` 和 `S2-R-008` 继续由 WP7/WP8 按阶段合同处理。WP6 不修改数据库结构，关闭 `AI_CHAT_ENABLED` 即可回退 AI 生成能力，不影响核心项目和任务功能。
