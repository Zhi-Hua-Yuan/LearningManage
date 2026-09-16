# Phase 0 设计与代码复核

## 结论

本分支实现与 Phase 0 合同一致，可以进入受保护 PR；Tag/Release 仍需等待合并后的双仓 Release Gate。

## 关键复核

- 配置测试通过 Spring Boot `Binder` 解析 shared/dev/test/prod 的实际合并结果，覆盖默认 `true`、环境变量 `false` 和测试场景局部关闭，避免仅做文本匹配。
- test profile 继续局部关闭外部 AI 路径，单元测试不会意外访问 Provider；Stage 7 与生产 Compose 门禁不提供五项开关覆盖值，专门验证默认开启。
- Tool Calling smoke 要求最终 Run 的 `orchestrationMode=TOOL_CALLING`。普通 AI 调用仍使用空 Tool 列表，Agent 只生成场景白名单定义；已有策略继续拒绝重复、未知与写 Tool。
- 核心 readiness 只包含 `readinessState,coreDatabase`，AI 依赖单独位于 `/actuator/health/ai`；默认开启不把外部 AI 故障升级成核心服务下线。
- Cleanup 默认值未改变，生产部署脚本仍拒绝 Cleanup 或 Cleanup Schedule 在新部署时为 `true`。
- OpenAPI baseline SHA-256 为 `6251fc7e067bffcdf648b2a3bc939c1bb808467debecc4dfb0e13339dae6f1b2`；运行时 `servers` 可规范化比较，原始文档仍归档。
- 未修改 V1～V8 migration 文件。

## 风险

- 默认开启会产生真实 Embedding/Rerank/Chat 调用，部署前仍需配置独立密钥、Provider 预算与 Qdrant/Redis。
- 本地环境不等同于 GitHub 受保护门禁；Docker 故障注入、MySQL 集成、E2E 和 Release Manifest 以合并候选 Gate 为准。
- 历史真实 Qwen 可观测性豁免仍未补跑。

## 本地验证（2026-09-15）

- Backend 非 MySQL：`834/834` PASS（最终复跑前一版为 833/833；新增幂等竞争回归后 +1）。
- 新增/受影响配置、CI 和 Tool 边界专项：`31/31`、`20/20` PASS。
- Frontend Vitest：`533/533` PASS；API contract：`3/3` PASS。
- Frontend lint、type-check、Vite production build：PASS。
- 生产部署、Stage 6、Stage 7 静态脚本：PASS；Stage 7 Compose 配置展开：PASS。
- OpenAPI baseline checksum：PASS；`git diff --check`：PASS。

隔离 MySQL、Docker 全栈、故障注入、E2E 和最终双 SHA Release Gate 必须在受保护 CI 中执行，当前不声称本地通过。
