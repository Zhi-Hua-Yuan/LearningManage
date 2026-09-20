# Phase 1 PR2 TODO

- [x] 建立 `codex/phase1-spring-ai-chat` 分支并保留现有未提交成果。
- [x] 完成 Legacy/Spring AI Adapter 选择器、非法值启动校验和 Docker 配置透传。
- [x] 收敛 Spring AI 依赖基线及 MVC 运行模式约束。
- [x] 保持治理逻辑集中在 `AiModelClientImpl`，移除旧 `invoke()`/`AiInvocationResult`。
- [x] 增加 transport-neutral `AiStreamingModelClient` 与终止元数据契约。
- [x] 增加自动配置收敛、异常分类、元数据缺失和 Streaming 契约测试。
- [x] 在 CI 中用同一确定性 Stub 分别运行 `legacy`、`spring-ai` 五场景评估，并完成标准化对等报告。
- [x] 完成后端、MySQL、Docker、前端和双仓精确 SHA Release Gate（run `35507744883`，结果 `PASS`）。
- [x] PR #170 合入 `develop`，并记录 merge SHA、配对前端 SHA、Gate 链接和 manifest 校验值。
- [x] 补齐最终 requirements、review、handover 和 acceptance manifest 收尾记录。

## 收尾状态

- 后端 `develop` merge SHA：`42fe52ed803f8ebe465e3d1b2809ef38df20dc0f`。
- 配对前端 `develop` SHA：`5561e8bdbfbfbc96b78f75dc6d8de4015f5ee430`。
- Phase 1 PR2：`PASS_WITH_REAL_QWEN_WAIVER`。
- 真实 Qwen：`WAIVED_NOT_RUN`；后续补跑必须记录模型、时间、请求 ID、风险和结果。

## 明确豁免

真实 Qwen 验证：`WAIVED_NOT_RUN`。原因是本阶段只验证适配器协议和确定性 Stub，避免把外部模型可用性误报为代码通过；后续补跑必须记录模型、时间、请求 ID、风险和结果。
