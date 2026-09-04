# WP4 AI 场景服务拆分验收

状态：`IMPLEMENTED / LOCAL PASS / CANDIDATE CI PENDING`
日期：2026-09-04

## 本地验收结果

| 项目 | 结果 | 证据 |
|---|---|---|
| 六个场景服务和三个支持服务 | PASS | `service.ai.*`、`service.impl.ai.*` |
| 薄门面 | PASS | `AiServiceImpl` 137 行，仅七个场景/支持依赖 |
| 架构边界 | 4/4 PASS | `AiSceneArchitectureTest` |
| 门面委托 | PASS | `AiServiceImplFacadeTest` |
| 支持服务 | 8/8 PASS | 模型选择、JSON 清理、草稿生命周期 |
| 任务拆解新增特征测试 | 3/3 PASS | 结构解析、业务校验、预览草稿 |
| WP4 新增后端测试 | 24/24 PASS | 总测试从 629 增至 653 |
| 非 MySQL 回归 | 596/596 PASS | 原 572 + WP4 24 |
| 完整 Maven/MySQL 验证 | 645/645 PASS（既有证据） | 隔离 Docker MySQL、Flyway V3、JAR 打包成功；本轮新增 8 项纯单元测试已在非 MySQL 回归通过，候选 CI 需汇总 653 项 |
| 前端回归 | 459/459 PASS | Vitest 58 个文件 |
| 前端契约 | PASS | 44 operations，SHA-256 `4f8cb8d3...f0b2f6` |
| 类型检查与生产构建 | PASS | `vue-tsc --build`、Vite 766 modules |
| 公共 Controller 变化 | 0 | WP4 未修改 Controller |
| 已发布迁移变化 | 0 | V1/V2/V3 哈希与 WP3 一致 |
| 业务直连模型调用 | 0 | 唯一调用点仍在 `AiInvocationPipeline` |

## 兼容性结论

当前本地候选已证明代码级、单元级、MySQL 集成级和前端构建级兼容。Prompt、解析、权限、持久化、事务和降级逻辑已从门面迁入唯一场景服务，公共 API 未变化。

## 正式封存条件

以下工作属于保护分支候选发布流程，不能由未提交工作树的本地结果代替：

1. 后端保护分支 CI 全部 Job 通过；
2. Docker 确定性 AI Stub 全链路通过；
3. 运行时 OpenAPI 对冻结前端 44 个 operation 的缺失数为 0；
4. 合并后证据绑定精确提交 SHA；
5. 将 `S2-A-008` 从 `PENDING` 更新为 `PASS`，随后创建 annotated tag `stage2-wp4-v1.0.0`。

`S2-R-008` 已获得 WP4 本地缓解证据，但按阶段计划继续保持 `OPEN`，直到 WP8 最终跨仓兼容验收。
