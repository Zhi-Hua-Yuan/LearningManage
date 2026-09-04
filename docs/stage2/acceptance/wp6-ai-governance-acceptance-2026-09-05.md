# WP6 AI 治理验收记录

状态：`PASS`

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

## 候选门禁

跨仓候选 Release Gate `33903357653` 已在合并提交
`88e09bb2c9487ba04a2245355fcdc59152ad8639` 上通过，10/10 Job 成功：

- 后端 709/709，前端 459/459；
- Flyway 空库和存量库升级通过；
- Docker 全栈运行、运行时 OpenAPI 与前端 44/44 operation 匹配通过，legacy 37 保持；
- Docker AI Stub 草稿闭环通过；
- Gitleaks、生成的 JAR/dist 产物扫描、仓库守卫、候选 Manifest 生成与 SHA-256 校验通过。

因此 `S2-A-010`、`S2-A-011` 已通过，`S2-R-005`、`S2-R-006`、`S2-R-007`、`S2-R-009` 已关闭。阶段 2 总体验收仍保留 `S2-A-012`，待 WP8 最终跨仓验收完成。
