# 阶段 2 AI 调用清单

状态：`FROZEN INPUT`
来源：当前 `AiController`、`AiService`、`AiServiceImpl`、`AiInvocationPipeline` 和 `AiModelClient` 静态检查。

| 场景 | 当前入口 | 当前模型路径 | 当前写入 | 阶段 2目标服务 | 状态 |
|---|---|---|---|---|---|
| 任务拆解兼容接口 | `POST /api/ai/breakdown` | Pipeline 2.0 | 无，返回预览数据 | `TaskBreakdownAiService` | WP3_PIPELINE_IMPLEMENTED |
| 任务拆解预览 | `/breakdown/preview` | Pipeline 2.0 | 创建 `ai_draft` | `TaskBreakdownAiService` | WP3_PIPELINE_IMPLEMENTED |
| 任务拆解确认 | `/breakdown/confirm` | 不应调用模型 | 项目、里程碑、任务 | `TaskBreakdownDraftHandler` | OPEN |
| 周复盘兼容润色 | `POST /api/ai/polish` | Pipeline 2.0 | 无，返回文本 | `WeeklyReviewAiService` | WP3_PIPELINE_IMPLEMENTED |
| 周复盘润色预览 | `/polish/preview` | Pipeline 2.0 | 创建 `ai_draft` | `WeeklyReviewAiService` | WP3_PIPELINE_IMPLEMENTED |
| 周复盘润色确认 | `/polish/confirm` | 不应调用模型 | 更新周复盘 | `WeeklyPolishDraftHandler` | OPEN |
| 今日任务排序 | `/today-order/recommend` | Pipeline 2.0 + 规则降级 | 无 | `TodayOrderAiService` | WP3_PIPELINE_IMPLEMENTED |
| 日报改名建议 | `/daily-review/suggest-rename` | Pipeline 2.0 + 规则降级 | 无 | `DailyRenameAiService` | WP3_PIPELINE_IMPLEMENTED |
| 清单重排预览 | `/list/replan/preview` | Pipeline 2.0 + 规则降级 | 创建 `ai_replan_operation/item` | `ListReplanAiService` | WP3_PIPELINE_IMPLEMENTED |
| 清单重排确认 | `/list/replan/confirm` | 不应调用模型 | 更新任务 | `ListReplanAiService` + `AiWriteGuard` | OPEN |
| 清单重排取消 | `/list/replan/cancel` | 不应调用模型 | 更新操作状态 | `ListReplanAiService` + 状态机 | OPEN |
| 内部 `chat` 辅助方法 | `AiServiceImpl.chat` | Pipeline `executeRaw` 受限适配 | 无 | WP4 评估归属 | WP3_PIPELINE_IMPLEMENTED |

## 收口规则

1. 每个 OPEN 项必须有对应实现 PR、测试证据和最终状态。
2. 阶段 2 结束前，`AiServiceImpl` 不得保留场景 Prompt 组装、直接 ModelClient 调用或场景 JSON 解析。
3. 任何新增 AI 场景必须先登记，再实现；未登记的调用在架构门禁中失败。
4. 生产代码中 `aiModelClient.chat(...)` 只允许出现在 Pipeline；旧 `invoke(...)` 只保留在 `AiModelClientImpl` 兼容适配器内部。

## WP3 调用点收口结果

- 业务代码直接调用 `AiModelClient`：0。
- 业务代码直接依赖 `AiHttpTransport`：0。
- 模型调用入口：`AiInvocationPipeline`。
- 任务拆解、周总结、今日排序、日报改名、清单重排和内部 chat 均已迁移。
- 场景类拆分仍属于 WP4；本节的 `IMPLEMENTED` 只代表模型调用路径已完成收口，正式验收仍由候选 CI 决定。

| 静态指标 | WP3 前 | WP3 后 |
|---|---:|---:|
| `AiServiceImpl` 直接模型调用点 | 3 | 0 |
| Pipeline 模型调用点 | 1 个旧 `invoke` | 1 个 `chat` |
| 重复日志生命周期辅助方法 `invokeAiWithLog` | 1 | 0 |
| 生产场景统一经过 Pipeline | 2 | 6 |

以上结果由 ArchUnit 和 `scripts/ci/verify-ai-invocation-boundary.sh` 双重约束；新增直接依赖或调用会使 CI 失败。

## WP1 冻结的数据模型

| 数据对象 | V3 能力 | WP1 状态 | 后续责任 |
|---|---|---|---|
| `ai_call_log` | requested/actual model、finish reason、供应商请求 ID、Usage、价格/成本、Trace、失败/回退/降级、脱敏/截断/哈希元数据 | Schema 已冻结；历史未知值保持 `null` 或 `LEGACY_UNKNOWN` | WP2 写入协议元数据，WP3/WP6 接入运行逻辑 |
| `ai_draft` | `schema_version=1`、可空 `trace_id` | Schema 已冻结，存量安全回填 | WP5 实现 Handler、行锁/CAS 和重放语义 |
| `ai_draft_confirm_log` | 可空 `trace_id`，唯一边界改为 `(user_id,draft_id)`，保留首次 `operation_id` | 数据库唯一底线已完成 | WP5 完整实现权限与幂等状态机 |
| `ai_draft_confirm_log_archive` | 原确认日志完整快照、归档原因/时间/迁移版本，`source_log_id` 唯一 | 仅用于 V3 等价重复迁移审计 | 不进入运行时 Mapper、Controller 或公共 API |
| `ai_replan_operation` | 可空 `trace_id` 与索引 | Schema 已冻结 | WP4/WP6 接入运行时 Trace |

V3 合并后不可修改；WP2 及以后若需要新字段，必须使用新的前向迁移，不得改写 V3。
