# Phase 0 交接与发布清单

## 合并前

1. 后端与前端分别从当前 `codex/phase0-baseline-freeze` 创建或更新 PR。
2. 保持功能修复、文档本地化等已有独立提交，不 squash 成无法审计的单个混合提交。
3. 确认 `docs/interview/` 未被跟踪、仓库中没有 `stop` 文件、V1～V8 checksum 验证通过。

## 合并后 Gate

从两个远端 `develop` 读取精确 SHA，触发 `Cross-repository release gate`。保存 run ID、测试数量、前端 operation 数量、OpenAPI baseline/breaking report SHA、生产 Manifest、真实模型证据与豁免项。

## Tag 与 Release

只有 Manifest 为 PASS 后，在两个 `develop` 精确提交上创建同名 annotated Tag：

```text
stage8-pre-spring-ai-v1.0.0
```

Tag 注释至少包含：前后端 SHA、Gate run ID、后端/前端测试数量、前端 API operation 数、OpenAPI 两项 SHA、V1～V8 checksum、真实模型证据和历史豁免、七项开关实际值、未豁免风险及回滚方式。两个仓库的 GitHub Release 使用同一份已核验说明。

## 回滚

优先按 Tool Calling → Agent Worker → Agent → RAG → Knowledge Worker 设置为 `false`，核心业务继续运行；Cleanup 保持关闭。代码级回滚部署本 Tag 对应的双 SHA 产物，不回滚 Flyway，并从 MySQL 通过 Outbox replay/rebuild 恢复派生索引。

