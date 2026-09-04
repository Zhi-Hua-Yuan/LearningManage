# WP6 AI 治理验收记录

状态：`LOCAL PASS / FORMAL GATE PENDING`

基线：`stage2-wp5-v1.0.0`，起点 `9570a64`

## 本地验收结论

- TraceId 过滤器、MDC 清理、响应头、Pipeline 和草稿/确认链路传播已实现。
- 模型请求和 AI 日志正文统一脱敏；日志服务是正文持久化唯一入口。
- Token、价格版本、币种、跨模型估算成本和聚合统计已实现，未知 Usage/价格不伪造。
- 全局 Bulkhead、模型级 Circuit Breaker、总期限和最多两次外部请求已实现。
- 功能开关、生产限流 fail-closed、公开错误码和唯一失败分类已实现。
- V1～V3 未修改，没有 V4、RAG、Qdrant 或 Agent 代码。

## 自动化证据

- 定向治理回归：44/44 通过。
- 完整 MySQL/Flyway 回归先后达到 701 和 704 项全通过；补齐新失败类型的内部/公开映射后，最终门槛为 709。
- 最终 709 项本地回归结果记录在 `evidence/wp6/local-verification.json`。
- 安全验证见 `evidence/wp6/security-scan-report.md`。
- 韧性故障注入见 `evidence/wp6/fault-injection-report.md`。

## 正式门禁

本文件不提前宣称候选发布完成。只有同一候选提交上的 Backend CI、Release Gate、前端 459 项、Docker Stub、Flyway 与运行时 API 44/44 全部通过后，才允许：

1. 将 `S2-A-010` 和 `S2-A-011` 改为 `PASS`；
2. 关闭 `S2-R-005`、`S2-R-006`、`S2-R-007`、`S2-R-009`；
3. 生成候选 Manifest、SHA-256 sidecar、Annotated Tag 和正式 Release。
