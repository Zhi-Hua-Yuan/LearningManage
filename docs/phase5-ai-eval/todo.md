# Phase 5 TODO

## 评测与证据

- [ ] 校验 5 场景、6 Prompt、RAG、Agent 的固定数据集和 hash。
- [ ] 执行 Legacy/Spring AI Stub 对等评测。
- [ ] 执行 1 轮 development、3 轮 regression、3 轮 holdout 的 Spring AI 真实评测。
- [ ] 完成至少 20% 语义结果人工抽检，agreement >= 80%。
- [ ] 绑定 backend SHA、frontend SHA、模型、Usage、Cost、Latency 和 provider request ID hash。

## Phase 4 收口

- [ ] 真实 Qwen Tool Calling 多轮验证。
- [ ] Spring AI 生产配置启动验证。
- [ ] 默认开启矩阵下的 MySQL、Redis、Qdrant、Docker 和前端 E2E。
- [ ] 失败项保留为诊断证据，不得改写为成功。

## 切换与下线

- [ ] Spring AI 默认运行 7 个连续自然日。
- [ ] 观察期内保留 Legacy 回退和 cutover 制品。
- [ ] 观察门禁通过后删除 Legacy Adapter、selector 和双轨配置。
- [ ] 创建 `stage9-spring-ai-v1.0.0` 前后端同名 Tag 和最终 Release Manifest。
