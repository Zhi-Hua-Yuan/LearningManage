# 阶段 2：AI 调用治理与协议升级

状态：`WP0/WP1/WP2 PASS · STAGE FROZEN`（模型协议实现完成，真实供应商验证留至 WP7）

阶段 2 不新增 RAG、Embedding、Qdrant 或 Agent 业务能力，目标是把现有 AI 调用收敛为可扩展、可观测、可降级且保持 API 兼容的基础设施，为阶段 3 评测和阶段 4～6 的 RAG/Agent 提供稳定底座。

## 设计输入

- [阶段 2 基线](baseline/2026-09-04.md)
- [阶段 2 需求合同](requirements/stage2-requirements-contract.md)
- [AI 调用清单](ai-invocation-inventory.md)
- [ADR-001：调用管线与模型协议](architecture/ADR-001-invocation-pipeline-and-model-protocol.md)
- [ADR-002：草稿确认幂等](architecture/ADR-002-draft-confirmation-idempotency.md)
- [ADR-003：日志、成本与韧性](architecture/ADR-003-logging-cost-and-resilience.md)
- [AI 调用接口合同](api/ai-invocation-contract.md)
- [阶段 2 风险登记表](risk/stage2-risk-register.md)
- [机器可读验收合同](acceptance/stage2-acceptance-contract.json)
- [WP1 V3 预审与迁移说明](database/wp1-v3-preflight-and-migration.md)
- [WP1 数据库验收记录](acceptance/wp1-v3-database-acceptance-2026-09-04.md)
- [WP2 模型协议](api/wp2-model-protocol.md)
- [WP2 模型协议验收记录](acceptance/wp2-model-protocol-acceptance-2026-09-04.md)

## 实施边界

本阶段只实现：

1. `AiModelClient.chat(...)` 及 Tool Calling 消息协议；
2. 所有可达 AI 场景统一经过 `AiInvocationPipeline`；
3. 场景服务拆分、失败分类、模型回退和规则降级；
4. AI 日志脱敏、截断、Trace、Usage、成本和价格版本；
5. 通用草稿确认 Handler、Schema 版本和草稿级幂等；
6. Chat 外部调用的超时、重试、熔断和并发隔离；
7. 现有前端和 API 契约兼容回归。

本阶段不实施数据库 V4、Qdrant、Embedding、Rerank、RAG 查询、Agent Tool 或新的 AI 业务场景。

## 验收入口

```bash
scripts/ci/verify-stage2-acceptance.sh
```

WP0～WP2 已通过，S2-A-005 和 S2-A-006 为 `PASS`。阶段总状态继续保持 `FROZEN`，因为 WP3～WP8 仍待实施。只有 WP3～WP8 全部通过后，才能将阶段合同改为 `PASS` 并发布 `stage2-v1.0.0`。
