# WP6 AI 安全可观测、成本治理与韧性需求合同

状态：`IMPLEMENTED / CANDIDATE CI PENDING`

基线：`stage2-wp5-v1.0.0`，起点提交 `9570a64`

## 范围

WP6 只治理现有 Chat 模型调用，不新增业务 API、数据库迁移、RAG、Qdrant、Embedding 或 Agent。

必须满足：

1. HTTP TraceId 在认证前建立，合法客户端值可透传，非法值重新生成，并在请求结束时清理 MDC。
2. 模型请求与日志正文统一经过 `AiContentSanitizer`；日志正文先脱敏、后截断，哈希基于脱敏后完整正文。
3. 新日志只产生 `CLEAN`、`REDACTED`、`BLOCKED`，正文只能由 `AiCallLogService` 持久化。
4. Token 只采用供应商 Usage；价格按实际模型和价格版本计算，未知值保持 `NULL`。
5. 全局 Semaphore Bulkhead 限制并发，每个实际模型维护独立 Circuit Breaker。
6. 主模型和兜底模型各最多一次，逻辑调用外部请求最多两次，不叠加同模型重试。
7. `AI_CHAT_ENABLED=false` 时生成入口关闭，但草稿查询、取消、确认和核心业务保持可用。
8. 生产 Redis 限流失败采用 fail-closed，开发与测试允许 fail-open。
9. 普通用户只能查询本人日志，TraceId 和供应商请求 ID 不构成授权凭证。

## 兼容边界

- 不删除或改名现有接口和字段，只为日志 VO 增加可选治理字段。
- V1、V2、V3 不修改，不新增 V4。
- 旧草稿协议、确认幂等语义和五类 AI 场景响应保持不变。
- 真实供应商协议与真实 Token 准确性保留到 WP7 验收。

## 发布门槛

- 后端完整测试不少于 WP5 的 674 项；本工作包门槛固定为 709 项。
- 安全、故障注入、MySQL、Flyway、Docker Stub 和跨仓 API 合同必须在同一不可漂移候选上通过。
- `S2-A-010`、`S2-A-011` 只有在候选 CI 完成后才能改为 `PASS`。
