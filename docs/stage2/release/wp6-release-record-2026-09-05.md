# WP6 Release 记录

状态：`PASS`

## 发布绑定

| 项目 | 值 |
|---|---|
| 工作包 | WP6 AI 安全可观测、成本治理与韧性保护 |
| 实现 PR | [#116](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/116) |
| 候选实现提交 | `88e09bb2c9487ba04a2245355fcdc59152ad8639` |
| 跨仓候选门禁 | [run 33903357653](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33903357653) |
| 前端候选提交 | `2ef907f292fbbacecf8a68f7d24c4701a555aa8a` |
| 候选名称 | `stage2-wp6-20260905-r3` |
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

- Candidate Manifest SHA-256：`1F8B37BDB0616D9C9679C177ABCD6C53DF459B5D5A74C9E13BE8AC7BCD70401B`
- Backend JAR SHA-256：`2C5D860C288B89AEC485D11C9DA662B0D504D21800CD85BBD50CD659490799F4`
- Backend artifact scan report SHA-256：`23BE9CA90305CAE015BD927DB5818CA75D58B47101D77E508A9D431575097306`
- Frontend artifact scan report SHA-256：`C2F9EDD5E0CC43FB02C3C691F34EB7432111F38FBD02AB30BFEB6CFC1B883FE9`
- API 比对报告 SHA-256：`AF13A31207019571A9FCFF5C61E4FECB0195831C88A03BE8AA073A31C4CFB5DE`
- 全栈 AI 证据 SHA-256：`1ECDE7E69E60AE5BB1803C3590A15C2227608786DA0ADB8B72693D686F26B0B2`
- 前端合同 SHA-256：`4F8CB8D3B92252E4375B49DD102E7CDE75F819827713060D6E521BED19F0B2F6`

## 范围说明

本 Release 封存阶段 2 的 WP6，不代表阶段 2 整体完成。`S2-A-012`、`S2-R-003` 和 `S2-R-008` 继续由 WP7/WP8 按阶段合同处理。WP6 不修改数据库结构，关闭 `AI_CHAT_ENABLED` 即可回退 AI 生成能力，不影响核心项目和任务功能。
