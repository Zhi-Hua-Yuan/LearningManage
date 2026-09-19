# Phase 0 TODO 与状态

| 优先级 | 工作项 | 状态 |
|---|---|---|
| P0 | Long/OpenAPI 与 Prompt 版本修复保留 | DONE |
| P0 | 40901 并发冲突后端映射与前端恢复 | DONE |
| P0 | 五项 AI 功能默认开启、显式关闭、Cleanup 默认关闭 | DONE |
| P0 | 静态配置绑定契约与 Tool 非全局注册契约 | DONE |
| P0 | Stage 7 默认开启集成门禁与生产 Release Gate 接入 | DONE |
| P0 | OpenAPI baseline、SHA-256、固定 oasdiff breaking gate 与 Manifest | DONE |
| P1 | Stage 8 架构、部署和 Bugfix 文档事实修正 | DONE |
| P1 | 前后端本地回归 | DONE |
| P0 | 后端/前端 PR 合入受保护 `develop` | DONE（后端 PR #166，前端 PR #61） |
| P0 | 精确双 SHA Release Gate | DONE（run #41 / 35355371404） |
| P0 | annotated Tag 与 GitHub Release | DONE（双仓 `stage8-pre-spring-ai-v1.0.0`） |

`PENDING_REMOTE` 与 `BLOCKED_BY_GATE` 不得改写为通过；本表最后三项是在取得对应证据后才改写为 `DONE` 的。后续 Tag 前仍必须再次确认两个仓库工作区干净、提交位于 `develop`、Gate Manifest 与两个 SHA 一致。

## Phase 0 收尾证据

- Release Gate：run #41（id `35355371404`），`workflow_dispatch` / `develop`，10/10 job success，窗口 `2026-09-18T14:18:07Z` → `2026-09-18T14:30:12Z`。
- 候选 `stage8-20260918-001`：后端 `3b5c9012fe5d4bd508a60e786123cf5cdeed146b`，前端 `5561e8bdbfbfbc96b78f75dc6d8de4015f5ee430`。
- Gate 前后两个 `develop` 均未推动（Manifest 内 `developShaAtStart == developShaAtEnd`），候选未过期。
- 候选 Manifest SHA-256：`4EBD9C901912585C6BDE6E30698B50F9E102A346DB5DA004A2C7D21DB8161152`
- 生产 Manifest SHA-256：`C6242AAE83FFF8B423F826119C5AA39501B28BA5DF1E34966DC5080B6281C5B9`
- 双仓 annotated Tag `stage8-pre-spring-ai-v1.0.0` 已创建并推送；远端 `refs/tags/…^{}` 解引用分别等于上述两个候选 SHA。
- 双仓 GitHub Release 已创建（非 draft、非 prerelease），使用同一份已核验说明，各附 15 份证据文件：两份 Manifest 及 `.sha256`、`runtime-openapi.json`、`api-contract-report.json`、`openapi-breaking-change-report.json`、`frontend-api-contract.json`、`full-stack-ai-flow-evidence.json` 及各自 `.sha256`。
- Stage 7 受保护的真实 Qwen 可观测性验证：**仍为用户显式豁免、未运行**，不得表述为通过。


