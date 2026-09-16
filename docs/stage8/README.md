# 阶段 8：系统设计与项目介绍

状态：`DOCUMENTATION_COMPLETE / LOCAL_SOURCE_AUDIT_PASS`

阶段 8 不新增运行时功能。它把阶段 0～7 已实现并有证据支撑的业务、AI 和运维能力整理成可维护的系统设计材料与口述介绍。

## 交付物

| 交付物 | 文件 |
|---|---|
| 系统架构图 | [architecture/system-architecture.md](architecture/system-architecture.md) |
| 权限矩阵 | [authorization/permission-matrix.md](authorization/permission-matrix.md) |
| AI 调用流程图 | [architecture/ai-invocation-flow.md](architecture/ai-invocation-flow.md) |
| Outbox 与索引一致性图 | [architecture/outbox-index-consistency.md](architecture/outbox-index-consistency.md) |
| RAG 检索和引用图 | [architecture/rag-retrieval-and-citation.md](architecture/rag-retrieval-and-citation.md) |
| Agent 状态机与 Tool Calling 图 | [architecture/agent-state-and-tool-calling.md](architecture/agent-state-and-tool-calling.md) |
| 项目总结 | [presentation/project-summary.md](presentation/project-summary.md) |
| 1、3、10 分钟项目介绍 | [presentation/project-introductions.md](presentation/project-introductions.md) |
| Phase 0 baseline 需求、TODO、复核与交接 | [../phase0-baseline/requirements.md](../phase0-baseline/requirements.md)、[../phase0-baseline/todo.md](../phase0-baseline/todo.md)、[../phase0-baseline/review.md](../phase0-baseline/review.md)、[../phase0-baseline/handover.md](../phase0-baseline/handover.md) |

## 阅读顺序

1. 先用系统架构图说明业务、AI、数据和运维边界。
2. 用权限矩阵解释为什么系统不会把平台管理员、团队管理员和业务资源所有者混为一谈。
3. 依次讲 AI 管线、Outbox、RAG 和 Agent，形成从模型调用到安全执行的递进关系。
4. 根据场合选择 1、3 或 10 分钟口述稿。

## 事实与声明边界

- 阶段 1、2、4、6 已有独立实现和验收/发布证据。
- 阶段 5 状态是 `IMPLEMENTATION_COMPLETE / RELEASE_NOT_PLANNED`；未创建独立 Tag 或 Release 是明确的发布决策，不代表 RAG 未实现。
- 阶段 7 已发布为 `stage7-v1.0.0`；受保护的真实 Qwen 可观测性验证由用户显式豁免，未运行且不能表述为通过。
- 阶段 3 的离线确定性评测不能代替最终真实模型语义质量与人工复核。项目介绍只把已完成的确定性门禁和真实供应商冒烟测试作为事实陈述。
- 系统当前不包含文件知识库、多轮通用聊天、混合关键词检索、多 Agent 或 AI 直接写正式业务数据。

## 权威来源

发生冲突时，以当前代码、已发布 Flyway V1～V8、阶段 ADR/API 合同和最新验收证据为准。本文档不覆盖历史决策，只提供面向阶段 8 的统一视图。

| 阶段 8 主题 | 主要证据来源 |
|---|---|
| 权限与隐私 | `docs/stage1/authorization/permission-matrix.md`、当前 `PermissionService`、团队生命周期实现 |
| AI 调用治理 | `docs/stage2/architecture/`、当前 `AiInvocationPipeline` 与 `AiModelClient` |
| 评测声明 | `docs/stage3/reports/current-baseline.md`、阶段 5/6 评测记录 |
| Outbox 与索引 | `docs/stage4/architecture/`、当前 `KnowledgeIndexServiceImpl` 与 Worker |
| RAG | `docs/stage5/architecture/` 与 `docs/stage5/api/rag-api.md` |
| Agent | `docs/stage6/README.md`、Agent API、状态机、Worker 与 Tool Policy |
| 运维和生命周期 | `docs/stage7/`、Flyway V8、健康指标与清理实现 |

本地源文件审计已验证：9 个交付文件存在，README 导航目标全部有效，15 个 Mermaid 代码块围栏成对，未发现行尾空白。仓库当前没有 Mermaid CLI，因此图形渲染仍以 GitHub/Codex Markdown 预览为准。
