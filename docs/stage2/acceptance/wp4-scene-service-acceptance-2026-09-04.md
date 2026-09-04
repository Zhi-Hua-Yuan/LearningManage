# WP4 AI 场景服务拆分验收

状态：`PASS / CANDIDATE CI PASS`
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
| 完整候选 Maven/MySQL 验证 | 653/653 PASS | 候选 run `33875362611`，Flyway 空库/存量库和 JAR 校验通过 |
| 前端回归 | 459/459 PASS | 候选 run `33875362611`，Vitest、类型检查和生产构建通过 |
| 前端契约与运行时 OpenAPI | 44/44 MATCH | 前端 44、运行时 65、缺失 0；报告 SHA-256 `595D7032...E6AD2EE03` |
| Docker Stub 全栈闭环 | PASS | Nginx、API Proxy、AI Stub、预览/取消/确认/幂等重放全部通过 |
| 类型检查与生产构建 | PASS | `vue-tsc --build`、Vite 766 modules |
| 公共 Controller 变化 | 0 | WP4 未修改 Controller |
| 已发布迁移变化 | 0 | V1/V2/V3 哈希与 WP3 一致 |
| 业务直连模型调用 | 0 | 唯一调用点仍在 `AiInvocationPipeline` |

## 兼容性结论

当前本地候选已证明代码级、单元级、MySQL 集成级和前端构建级兼容。Prompt、解析、权限、持久化、事务和降级逻辑已从门面迁入唯一场景服务，公共 API 未变化。

## 正式封存结果

候选 release gate `33875362611` 已在精确后端 SHA `d3a9c673e2e79709f2cd140be9c24cad874957bd` 和前端 SHA `2ef907f292fbbacecf8a68f7d24c4701a555aa8a` 上通过。后端、前端、Flyway、Docker Stub、运行时 OpenAPI、AI 草稿闭环和候选 manifest 证据已绑定，`S2-A-008` 已更新为 `PASS`。

`S2-R-008` 已获得候选 CI、Docker 和运行时 API 缓解证据，但按阶段计划继续保持 `OPEN`，直到 WP8 最终跨仓兼容验收。
