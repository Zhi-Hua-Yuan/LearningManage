# 阶段 0 CI 与迁移发布门禁

状态：PR6-A、PR6-B、PR6-C、PR6-D1、PR6-D2-B2、PR6-D2-C 已完成；可进入 PR6-D3～D4 阶段 0 总验收

## 目标

建立可重复执行的后端 CI 脚本、存量库接管 fixture 和已发布迁移不可变门禁，为后续 GitHub Actions 接入提供稳定基础。

## 当前边界

- 已发布迁移的机器可读哈希清单位于 src/test/resources/flyway/published-migrations.sha256。

- 本目录不保存凭据、数据库备份或业务数据。
- PR6-A 不创建 GitHub Actions 工作流。
- 数据库验证只能针对明确授权的临时 CI 数据库。
- 禁止连接或修改 3306 `learning_manage` 主库。
- 禁止执行 `flyway clean`。
- 已发布的 V1 迁移不可修改。

## PR6 拆分

| 阶段 | 内容 | 状态 |
|---|---|---|
| PR6-A | 后端 CI 脚本、存量库 fixture、V1 不可变检查 | 已完成 |
| PR6-B | 后端 GitHub Actions、Docker 门禁和分支保护 | 已完成 |
| PR6-C | 前端测试、前端 CI 和分支保护 | 已完成 |
| PR6-D1 | 跨仓候选发布工作流 | 已完成 |
| PR6-D2-C | Nginx 全栈运行、确定性 AI 流程和幂等门禁 | 已完成（第 004 候选全绿） |
| PR6-D3～D4 | 阶段 0 验收和证据封存 | 待 PR6-D2-C 通过后执行 |

## PR6-A 实施步骤

| 步骤 | 内容 | 状态 |
|---|---|---|
| A1 | 建立目录和文档骨架 | 已完成 |
| A2 | 生成并审查存量库 fixture | 已完成 |
| A3 | 实现 V1 和已发布迁移不可变检查 | 已完成 |
| A4 | 实现跨平台 CI 脚本 | 已完成 |
| A5 | 隔离 MySQL 端到端验证 | 已完成 |
| A6 | 完成记录、复核和提交 | 已完成 |

## PR6-B 实施步骤

| 步骤 | 内容 | 状态 |
|---|---|---|
| B1 | Linux构建权限和Docker构建上下文 | 已完成 |
| B2 | Dockerfile、CI Compose和静态运行契约 | 已完成 |
| B3 | 后端GitHub Actions编排与远程Runner验收 | 已完成 |
| B4 | 单人仓库Ruleset、必需状态检查和验证PR | 已完成 |

## 文档索引

- [PR6-A 执行记录](pr6-a-backend-ci-foundation-2026-08-20.md)
- [CI 脚本说明](../../../scripts/ci/README.md)
- [存量库 fixture 说明](../../../src/test/resources/db/legacy/README.md)
- [CI Docker Compose](../../../deploy/docker-compose.ci.yml)
- [PR6-B2 Docker运行契约记录](pr6-b2-docker-runtime-contract-2026-08-21.md)
- [PR6-B3 GitHub Actions远程验收记录](pr6-b3-backend-github-actions-2026-08-21.md)
- [PR6-B4 分支保护执行记录](pr6-b4-branch-protection-2026-08-21.md)
- [PR6-D1 跨仓候选发布工作流](pr6-d1-cross-repo-candidate-workflow-2026-08-21.md)
- [PR6-D2-B2 运行时 OpenAPI 契约门禁](pr6-d2-b2-runtime-openapi-contract-2026-08-22.md)
- [PR6-D2-C 全栈 AI 门禁执行记录](pr6-d2-c-full-stack-ai-gate-2026-08-22.md)
- [跨仓候选发布运行手册](release-gate-runbook.md)
- [候选 Manifest Schema](release-candidate-manifest.schema.json)
