# 前后端接口差异

来源：当前运行中的后端 `/api/v3/api-docs` 与前端 `src/api/*.ts` 的确定性契约导出。

| 指标 | 数量 |
|---|---:|
| 前端调用操作 | 71 |
| 后端运行时操作 | 95 |
| 前端操作在后端缺失 | 0 |
| 后端未被前端 API 层调用 | 24 |

结论：路径和 HTTP 方法没有缺失；主要接口缺陷是错误响应的 HTTP 状态语义，见
`BUG-AUDIT-002`。

## 后端独有操作

这些接口不是路径错误；其中一部分属于管理、兼容或后端内部验收能力：

```text
GET  /admin/ai/knowledge/backfills/{runId}
GET  /admin/ai/knowledge/events
GET  /admin/ai/knowledge/status
POST /admin/ai/knowledge/backfills
POST /admin/ai/knowledge/events/{eventId}/replay
GET  /admin/ai/ops/agent
GET  /admin/ai/ops/dependencies
GET  /admin/ai/ops/rag
GET  /ai/call-log/get/{id}
GET  /ai/call-log/list
GET  /ai/call-log/stats
POST /ai/breakdown
POST /ai/daily-review/suggest-rename
POST /ai/polish/confirm
POST /ai/polish/preview
GET  /demo/error/business
GET  /demo/error/system
GET  /demo/error/validate
GET  /health
GET  /project/get/{id}
POST /project/team/create
POST /task/batch-rename
POST /task/batch-rename/rollback
GET  /task/get/{id}
```

## DTO 与运行时抽查

以下高风险结构通过实际请求响应验证：

- 登录 Token、`/user/me` 角色；
- 项目、任务和 `assigneeUserId`；
- 团队 ID、邀请码、成员退出及 `unassignedTaskCount`；
- 团队周复盘仅包含共享投影，未出现 `reflection`、`nextPlan`、`taskIds`；
- AI 草稿 `draftId`、取消状态与取消后确认冲突；
- 管理接口权限拒绝；
- RAG sources 与 Agent draft 的 Stage 7 smoke。

前端所有 71 个路径/方法都已匹配运行时 OpenAPI。TypeScript 与 OpenAPI 的深层字段类型
目前没有统一的机器生成来源，因此未声明“所有嵌套 DTO 均由机器证明一致”；该差距记录为
后续合同治理风险。
