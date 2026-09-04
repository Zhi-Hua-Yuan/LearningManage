# 阶段 2 AI 调用清单

状态：`FROZEN INPUT`
来源：当前 `AiController`、`AiService`、`AiServiceImpl`、`AiInvocationPipeline` 和 `AiModelClient` 静态检查。

| 场景 | 当前入口 | 当前模型路径 | 当前写入 | 阶段 2目标服务 | 状态 |
|---|---|---|---|---|---|
| 任务拆解兼容接口 | `POST /api/ai/breakdown` | 通过旧门面进入拆解逻辑 | 无，返回预览数据 | `TaskBreakdownAiService` | OPEN |
| 任务拆解预览 | `/breakdown/preview` | 部分进入 Pipeline，需核对全部路径 | 创建 `ai_draft` | `TaskBreakdownAiService` | OPEN |
| 任务拆解确认 | `/breakdown/confirm` | 不应调用模型 | 项目、里程碑、任务 | `TaskBreakdownDraftHandler` | OPEN |
| 周复盘兼容润色 | `POST /api/ai/polish` | 旧兼容路径 | 无，返回文本 | `WeeklyReviewAiService` | OPEN |
| 周复盘润色预览 | `/polish/preview` | 需统一进入 Pipeline | 创建 `ai_draft` | `WeeklyReviewAiService` | OPEN |
| 周复盘润色确认 | `/polish/confirm` | 不应调用模型 | 更新周复盘 | `WeeklyPolishDraftHandler` | OPEN |
| 今日任务排序 | `/today-order/recommend` | 已使用 Pipeline 的部分路径 | 无 | `TodayOrderAiService` | OPEN |
| 日报改名建议 | `/daily-review/suggest-rename` | 已使用 Pipeline 的部分路径 | 无 | `DailyRenameAiService` | OPEN |
| 清单重排预览 | `/list/replan/preview` | 当前仍有直接调用风险 | 创建 `ai_replan_operation/item` | `ListReplanAiService` | OPEN |
| 清单重排确认 | `/list/replan/confirm` | 不应调用模型 | 更新任务 | `ListReplanAiService` + `AiWriteGuard` | OPEN |
| 清单重排取消 | `/list/replan/cancel` | 不应调用模型 | 更新操作状态 | `ListReplanAiService` + 状态机 | OPEN |
| 内部 `chat` 辅助方法 | `AiServiceImpl.chat` | 当前可能直接调用 ModelClient | 无 | 迁移为 Pipeline 适配或移除旁路 | OPEN |

## 收口规则

1. 每个 OPEN 项必须有对应实现 PR、测试证据和最终状态。
2. 阶段 2 结束前，`AiServiceImpl` 不得保留场景 Prompt 组装、直接 ModelClient 调用或场景 JSON 解析。
3. 任何新增 AI 场景必须先登记，再实现；未登记的调用在架构门禁中失败。
4. `aiModelClient.invoke(...)` 的允许调用者只限兼容适配器和 Pipeline 测试 Stub。
