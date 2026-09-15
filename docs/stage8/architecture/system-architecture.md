# LearningManage 系统架构图

## 1. 架构目标

LearningManage 是一个 AI 原生项目与学习管理系统。系统保留项目、里程碑、任务、团队和周复盘等确定性业务能力，在统一权限边界之上增加受治理的 AI 生成、可重建知识索引、权限感知 RAG 和只读 Agent。

核心原则：

- MySQL 是唯一业务事实来源，Qdrant 是可删除、可重建的派生索引。
- 所有资源访问通过 `PermissionService`，`SYSTEM_ADMIN` 不自动获得私人业务数据访问权。
- 所有模型调用通过 `AiInvocationPipeline`，统一处理 Prompt、日志、用量、失败和降级。
- AI 写操作只生成草稿，必须由用户确认后才能写入正式业务表。
- RAG 来源和 Tool 输出都是不可信输入，必须重建、鉴权、限长和校验。
- Redis、Qdrant、模型或可观测组件故障不能改变核心业务 Readiness。

## 2. 总体架构

```mermaid
flowchart TB
    subgraph Client["客户端"]
        Browser["浏览器"]
        Vue["Vue 3 前端\n权限化 UI / 安全 Markdown / 异步轮询"]
        Browser --> Vue
    end

    subgraph Edge["接入层"]
        Nginx["Nginx\n静态资源 / API 反向代理"]
        Api["Spring Boot REST API\nBaseResponse / OpenAPI / X-Trace-Id"]
        Auth["JWT 拦截器\nActor 上下文"]
        Vue --> Nginx --> Api --> Auth
    end

    subgraph Application["应用与业务层"]
        Core["核心业务服务\n项目 / 里程碑 / 任务 / 团队 / 周复盘"]
        Permission["PermissionService\n项目范围 / 团队角色 / 批量鉴权"]
        Scene["AI 场景服务\n拆解 / 排序 / 重排 / 复盘"]
        Draft["AI 草稿生命周期\n行锁 + CAS + 幂等确认"]
        Rag["RAG Service\n检索 / 重建 / 重排 / 引用"]
        Agent["Agent Orchestrator\n异步 Run / 状态机 / 只读 Tool"]
        Auth --> Core
        Auth --> Scene
        Auth --> Rag
        Auth --> Agent
        Core --> Permission
        Scene --> Permission
        Rag --> Permission
        Agent --> Permission
        Scene --> Draft
        Agent --> Draft
    end

    subgraph AIPlatform["AI 基础设施"]
        Pipeline["AiInvocationPipeline\nPrompt / Trace / Usage / 成本 / 降级"]
        ModelClient["AiModelClient\nChat + Tool Calling + 主备模型"]
        EmbedClient["EmbeddingClient\ntext-embedding-v4 / 1024 维"]
        RerankClient["RerankClient\nqwen3-rerank"]
        Scene --> Pipeline
        Rag --> Pipeline
        Agent --> Pipeline
        Pipeline --> ModelClient
        Rag --> EmbedClient
        Rag --> RerankClient
    end

    subgraph Data["数据与异步一致性"]
        MySQL[("MySQL 8\n业务事实 / 草稿 / 审计 / Run / Outbox")]
        Redis[("Redis 7.4\n限流 / 非关键缓存")]
        Outbox["Transactional Outbox"]
        IndexWorker["Knowledge Worker\n租约 / Fence Token / 对账"]
        AgentWorker["Agent Worker\n租约 / 心跳 / 超时 / 取消"]
        Qdrant[("Qdrant 1.18.2\n向量与过滤 Payload")]
        Core --> MySQL
        Draft --> MySQL
        Rag --> MySQL
        Agent --> MySQL
        Scene --> Redis
        MySQL --> Outbox --> IndexWorker
        IndexWorker --> EmbedClient
        IndexWorker --> Qdrant
        Rag --> Qdrant
        MySQL --> AgentWorker --> Agent
    end

    subgraph Providers["外部供应商边界"]
        Qwen["阿里云百炼 / Qwen\nChat / Embedding / Rerank"]
        ModelClient --> Qwen
        EmbedClient --> Qwen
        RerankClient --> Qwen
    end

    subgraph Operations["运维与交付"]
        Actuator["私有 Actuator :9123\nLiveness / Core / AI Dependencies"]
        Metrics["Micrometer / Prometheus"]
        Trace["OpenTelemetry / Tempo"]
        Grafana["Grafana\n6 个仪表盘 / 10 条告警"]
        Flyway["Flyway V1～V8\n前向迁移 / 不可变校验"]
        CI["GitHub Actions\n测试 / 契约 / 迁移 / Docker / 证据"]
        Api --> Actuator
        Api --> Metrics --> Grafana
        Api --> Trace --> Grafana
        Flyway --> MySQL
        CI --> Flyway
    end
```

## 3. 关键业务链路

```mermaid
flowchart LR
    Goal["输入目标"] --> Breakdown["AI 任务拆解"]
    Breakdown --> Draft["生成草稿"]
    Draft --> Confirm{"用户确认?"}
    Confirm -- "否" --> Cancel["取消 / 过期"]
    Confirm -- "是" --> Project["项目 + 里程碑 + 任务"]
    Project --> Work["任务分配与推进"]
    Work --> Review["私人复盘 / 团队共享摘要"]
    Project --> Outbox["同事务 Outbox"]
    Review --> Outbox
    Outbox --> Index["异步知识索引"]
    Index --> RAG["RAG 引用回答"]
    RAG --> Agent["Agent 风险 / 负载分析"]
    Agent --> ReportDraft["分析草稿"]
    ReportDraft --> ReportConfirm{"用户确认?"}
    ReportConfirm -- "是" --> Report["正式分析报告"]
    ReportConfirm -- "否" --> End["不写正式数据"]
```

## 4. 分层职责

| 层次 | 主要职责 | 明确不负责 |
|---|---|---|
| Controller | 参数校验、认证入口、统一响应 | 资源权限规则和模型协议 |
| 业务/场景 Service | 用例编排、事务、业务校验和规则降级 | 供应商 HTTP 细节 |
| PermissionService | 根据服务端事实计算资源权限 | 接受客户端角色或能力声明 |
| AiInvocationPipeline | Prompt、模型调用、日志终态、Usage、失败分类 | 项目/任务权限和正式写入 |
| Model/Embedding/Rerank Client | 供应商协议、超时、主备模型与响应解析 | 业务授权和业务结果判断 |
| Knowledge Worker | 根据 MySQL 当前状态对账 Qdrant | 把事件正文当作事实快照 |
| RAG Service | 三次鉴权、来源重建、重排和证据校验 | 信任 Qdrant Payload 正文 |
| Agent | 受控调用场景白名单中的只读 Tool | 动态注册 Tool 或直接修改业务数据 |
| 运维层 | 健康、指标、追踪、保留和受控清理 | 向普通用户暴露平台级数据 |

## 5. 部署和故障边界

- 公共业务 API 使用应用端口；Actuator 使用不映射到宿主机的私有管理端口 `9123`。
- MySQL 故障使 Core Readiness 为 `DOWN`；Redis、Qdrant、模型和遥测故障只使 AI 依赖状态为 `DEGRADED`。
- RAG、Agent、Agent Worker、Knowledge Worker 均有独立功能开关，默认安全关闭后再按环境启用。
- Qdrant 丢失后通过 MySQL 文档元数据和幂等 Backfill 重建；Redis 丢失不影响正式业务事实。
- 数据库结构只通过 Flyway 前向迁移，应用账号保持 DML-only。

## 6. 当前交付状态

- 阶段 5 RAG 已实现并验证，但按项目决策不单独创建 Release。
- 阶段 7 已通过本地迁移、Docker、故障演练、指标和 Trace 验收，并发布为 `stage7-v1.0.0`；受保护的真实 Qwen 可观测性验证在发布时由用户显式豁免，不能表述为通过。
