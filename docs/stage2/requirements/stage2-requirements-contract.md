# 阶段 2 需求合同：AI 调用治理与协议升级

状态：`FROZEN`
建立日期：2026-09-04
前置版本：`stage1-v1.0.0`

## 1. 功能需求

| ID | 要求 | 验收方式 |
|---|---|---|
| S2-F-001 | 阶段 2 从最新受保护 develop 和阶段 1 发布证据开始，V1/V2 不可修改 | 分支祖先校验、迁移校验和候选 Manifest |
| S2-F-002 | 新增 `AiModelClient.chat(AiChatCommand)`，支持多角色、tools、tool_choice、tool_calls、finish_reason 和 usage | 模型协议契约测试和离线 Stub |
| S2-F-003 | 旧 `invoke(...)` 委托到新协议，现有 API、字段和响应结构保持兼容 | 37 个 legacy API、44 个前端 API 契约回归 |
| S2-F-004 | 所有可达模型调用统一经过 `AiInvocationPipeline`，场景服务不得直接调用 ModelClient/Transport | 静态架构门禁和调用覆盖清单 |
| S2-F-005 | 任务拆解、周复盘润色、今日排序、日报改名和清单重排拆分为独立场景服务 | 场景单元、集成和新旧结果对照测试 |
| S2-F-006 | 模型失败区分配置、认证、限流、网络、超时、上游、协议、解析、业务校验和内部错误 | 异常分类矩阵 |
| S2-F-007 | 可重试错误最多执行主模型一次、兜底模型一次；场景解析/业务校验失败进入规则降级 | 重试次数、回退原因和降级结果测试 |
| S2-F-008 | AI 调用日志记录 Trace、requested/actual model、finish reason、Usage、成本和价格版本 | 数据库字段、日志和成本计算测试 |
| S2-F-009 | 请求/响应正文先经过数据分级和脱敏，再按长度截断；保存正文哈希和截断标记 | API Key、JWT、密码、Authorization、个人信息脱敏测试 |
| S2-F-010 | 草稿确认使用场景 Handler、Schema 版本、行锁/CAS 和 `(user_id,draft_id)` 草稿级唯一 | 重复确认、不同 operationId、并发确认/取消测试 |
| S2-F-011 | Chat 调用具备超时、一次重试、Bulkhead 和 Circuit Breaker，配置错误启动即失败 | 故障注入和恢复测试 |
| S2-F-012 | 阶段 2 不新增 RAG、Embedding、Qdrant、Rerank、Agent Tool 或直接写入接口 | 静态范围门禁 |

## 2. 非功能需求

| ID | 要求 |
|---|---|
| S2-NF-001 | 所有既有后端测试和前端测试通过，不以减少测试用例数作为完成手段 |
| S2-NF-002 | 现有 legacy 37 个 operation 缺失数为 0，前端 44 个 operation 运行时匹配数为 44 |
| S2-NF-003 | 未经脱敏的 API Key、JWT、密码、Authorization 和 Cookie 进入日志的数量为 0 |
| S2-NF-004 | AI 日志写入失败不得改变业务调用结果，但必须产生可观测告警 |
| S2-NF-005 | 供应商 429、超时、网络和 5xx 不得阻塞业务线程超过配置上限 |
| S2-NF-006 | 所有状态变更使用明确 CAS/行锁条件，重复确认正式写入数量为 0 |
| S2-NF-007 | Token 缺失时保存 `null`，不得把未知 Usage 记为 0；成本必须绑定价格版本 |
| S2-NF-008 | 真实模型验证不在普通 PR 中注入密钥，受控验证证据不得包含密钥和完整敏感正文 |

## 3. 交付工作包

```text
WP0 基线、需求合同、调用清单、ADR、风险登记、机器验收合同
WP1 V3 数据库迁移预审、草稿幂等数据审计和字段迁移
WP2 Chat 消息协议、Tool Calling 类型和供应商兼容层
WP3 Pipeline 2.0、失败分类、模型回退和公共元数据
WP4 场景服务拆分及所有直接调用收口
WP5 通用草稿 Handler、确认状态机和草稿级幂等
WP6 脱敏、Token/成本、Trace、限流、熔断和并发隔离
WP7 现有前端兼容、真实模型受控验证和安全渲染
WP8 全量门禁、跨仓候选、证据封存和 stage2 Release
```

## 4. 非目标

- 不实现 RAG 查询接口。
- 不引入 Qdrant、Embedding 或 Rerank。
- 不实现 AgentOrchestrator、Agent Run 或业务 Tool。
- 不删除 `invoke(...)` 兼容方法。
- 不重做现有前端 AI 页面。
- 不把清单重排旧表强行迁移为新草稿表。
- 不把真实模型质量指标写入阶段 2 发布门槛；质量评测由阶段 3负责。
