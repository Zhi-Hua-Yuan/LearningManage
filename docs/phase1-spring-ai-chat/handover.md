# Phase 1 PR2 Handover

## 使用方式

```text
AI_CHAT_ADAPTER=legacy     # 默认，生产回滚基线
AI_CHAT_ADAPTER=spring-ai  # 确定性 Stub/受控验证时启用
```

配置只接受 `legacy` 和 `spring-ai`；其他值必须在启动阶段失败。生产默认仍为 `legacy`，代码级回滚基线为 `stage8-pre-spring-ai-v1.0.0`。

## 合入基线与 Gate 证据

| 项目 | 值 |
|---|---|
| PR | [#170](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/170) |
| 合入时间 | `2026-09-20T11:18:43Z` |
| 后端 `develop` merge SHA | `42fe52ed803f8ebe465e3d1b2809ef38df20dc0f` |
| 前端配对 `develop` SHA | `5561e8bdbfbfbc96b78f75dc6d8de4015f5ee430` |
| Cross-repository Release Gate | [run 35507744883](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/35507744883) |
| Candidate | `phase1-postmerge-20260920-001` |
| Candidate manifest | `PASS`；SHA-256 `0EDE3F7AD6DA9D1F93BEE69EC7D0ADF1A3212673396E70586C379E8870D88AFA` |
| 后端测试总数 | `959` |

Gate 已覆盖后端验证、隔离 MySQL、前端测试与构建、Flyway、Docker 全栈、API/OpenAPI 兼容性和 AI breakdown 生命周期。Stage 0 的 `PROVISIONAL` 绑定及 2 个 pending closing gates 仍按候选 manifest 保留，不在本 Phase 1 文档中扩大解释为已关闭。

## 验证记录要求

合入后证据已保存：非 MySQL Maven verification、隔离 MySQL/Flyway、依赖和边界门禁、Docker 全栈、API/OpenAPI、前端和双仓 Release Gate 的链接、精确 SHA 与测试总数。真实 Qwen 项目固定记录：`WAIVED_NOT_RUN`，原因是本阶段只验证确定性 Stub 协议对等性；风险是外部模型连通性、限流、真实 Usage/延迟和供应商 request ID 行为尚未验证，后续补跑必须记录模型、时间、请求 ID、风险和结果。

## Phase 2 入口

仅在 PR 合入、两套 Adapter 确定性对等验证通过、全门禁通过且豁免被准确记录后进入 Phase 2；不得在本阶段提前迁移 Structured Output、RAG Streaming 或 Agent ToolCallback。
