# 阶段 2：AI 调用治理与协议升级

状态：`WP0～WP6 PASS · STAGE FROZEN`

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
- [WP3 调用管线验收记录](acceptance/wp3-invocation-pipeline-acceptance-2026-09-04.md)
- [ADR-005：AI 场景服务拆分](architecture/ADR-005-ai-scene-service-decomposition.md)
- [WP4 场景服务内部合同](api/wp4-scene-service-contract.md)
- [WP4 场景服务拆分验收记录](acceptance/wp4-scene-service-acceptance-2026-09-04.md)
- [ADR-006：AI 草稿生命周期与写入安全](architecture/ADR-006-draft-lifecycle-write-safety.md)
- [WP5 草稿生命周期内部合同](api/wp5-draft-lifecycle-contract.md)
- [WP5 草稿生命周期验收记录](acceptance/wp5-draft-lifecycle-acceptance-2026-09-04.md)
- [WP5 需求与实施清单](requirements/wp5-draft-lifecycle-requirements.md)
- [WP5 候选门禁证据](evidence/wp5/candidate-release-gate-2026-09-04.json)
- [WP5 Release 记录](release/wp5-release-record-2026-09-04.md)
- [WP6 需求合同](requirements/wp6-ai-governance-requirements.md)
- [WP6 AI 治理内部合同](api/wp6-ai-governance-contract.md)
- [WP6 验收记录](acceptance/wp6-ai-governance-acceptance-2026-09-05.md)
- [WP6 机器证据](evidence/wp6/local-verification.json)
- [WP6 Release 记录](release/wp6-release-record-2026-09-05.md)

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

WP0～WP6 已正式通过，S2-A-005～S2-A-011 为 `PASS`。WP6 候选 Release Gate 已完成后端、前端、Flyway、Docker 全栈、API 契约和产物敏感信息扫描；阶段总状态继续保持 `FROZEN`，只有 WP7～WP8 全部正式通过后，才能将阶段合同改为 `PASS` 并发布 `stage2-v1.0.0`。
