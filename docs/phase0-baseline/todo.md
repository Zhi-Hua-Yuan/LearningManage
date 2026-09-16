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
| P0 | 后端/前端 PR 合入受保护 `develop` | PENDING_REMOTE |
| P0 | 精确双 SHA Release Gate | PENDING_REMOTE |
| P0 | annotated Tag 与 GitHub Release | BLOCKED_BY_GATE |

`PENDING_REMOTE` 与 `BLOCKED_BY_GATE` 不得改写为通过；Tag 前必须再次确认两个仓库工作区干净、提交位于 `develop`、Gate Manifest 与两个 SHA 一致。

