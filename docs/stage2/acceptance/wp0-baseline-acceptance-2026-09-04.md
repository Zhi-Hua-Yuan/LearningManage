# WP0：阶段 2 基线与需求冻结验收记录

状态：`PASS`（WP0 文档与合同已冻结）
日期：2026-09-04
前置版本：`stage1-v1.0.0`

## 1. 交付范围

WP0 只建立阶段 2 的实施输入和机器验收边界，不修改 Java、前端、数据库迁移、配置或现有 CI 工作流。

已交付：

- 阶段 2 基线和阶段 1 兼容基线；
- 阶段 2 需求合同与明确非目标；
- AI 调用入口和直接调用风险清单；
- 调用管线/模型协议、草稿幂等、日志/成本/韧性三份 ADR；
- AI 调用接口和失败映射合同；
- 阶段 2 风险登记表；
- 机器可读验收合同、Schema 和验证脚本。

## 2. 验收结果

| 检查项 | 结果 | 证据 |
|---|---|---|
| 阶段 1 发布 SHA、测试和 API 基线已记录 | PASS | `docs/stage2/baseline/2026-09-04.md` |
| 阶段 2 需求和非目标冻结 | PASS | `docs/stage2/requirements/stage2-requirements-contract.md` |
| ADR 设计输入已接受 | PASS | `docs/stage2/architecture/` |
| AI 调用入口清单已登记 | PASS | `docs/stage2/ai-invocation-inventory.md` |
| 机器合同 JSON Schema | PASS | `json_schema.status=PASS` |
| 合同语义检查 | PASS | `stage2.contract.semantic=PASS` |
| 文档和脚本 LF/空白检查 | PASS | `stage2.whitespace=PASS` |
| 阶段 2 文档敏感信息扫描 | PASS | `stage2.secret_scan=PASS` |
| Bash 校验脚本语法 | PASS | `stage2.script.syntax=PASS` |
| V1/V2、Java、配置和 CI 未被 WP0 修改 | PASS | 工作区路径边界检查 |

## 3. 环境说明与延期事项

- 当前本地工作区位于 `codex/wp8-release-evidence`；由于本机 `.git/refs` 权限限制，WP0 未创建新分支。进入 WP1 前必须在受保护的最新 `develop` 上创建阶段 2 分支，并校验阶段 1 候选 SHA 和最终证据提交均为祖先。
- 本机未安装 `jq`，因此未在 Windows 本机执行 `verify-stage2-acceptance.sh` 的完整运行态；GitHub Linux Runner 必须在 WP0 合并门禁中执行该脚本。JSON Schema 和同等语义检查已在本机通过。
- 阶段 2 合同状态保持 `FROZEN`，S2-A-005～S2-A-012 必须由 WP1～WP8 分别关闭。

## 4. 结论

WP0 验收通过，阶段 2 的实现可以从 WP1（V3 数据库迁移预审与草稿幂等数据审计）开始。WP0 不授权执行 V3 迁移，也不授权接入 RAG、Qdrant 或 Agent。
