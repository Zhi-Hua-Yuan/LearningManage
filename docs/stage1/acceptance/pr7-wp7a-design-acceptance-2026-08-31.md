# PR7 / WP7-A 前端合同冻结验收记录

状态：`ACCEPTED / MERGED / CI_PASS`

验收日期：2026-08-31

## 1. 验收结论

WP7-A 已完成设计交付并完成受保护 PR 合并：PR7 的范围、API 与字段、UI 权限、周复盘隐私、状态与缓存、错误处理和测试矩阵均已形成冻结合同，并由机器可读合同固定基线与后续工作包门禁。

本记录不宣称 PR7 功能已经实现，也不提前关闭 `S1-A-009`、`S1-A-010`、`S1-R-010` 或 `S1-R-013`；这些门禁和风险仍由 WP7-B～F 负责。

## 1.1 合并证据

| 项目 | 结果 |
|---|---|
| Pull Request | [PR #66](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/66) |
| Merge commit | `f911095aeca583b61a62fbbfdfde8fc3b153bc75` |
| 合并时间 | 2026-08-31 11:31:59（Asia/Shanghai） |
| CI run | `33353781042` |
| CI 结果 | Guard、Maven、Flyway empty、Flyway existing、Docker runtime/migration 全部 `SUCCESS` |

## 2. 固定基线

| 项目 | 固定值 |
|---|---|
| 后端仓库 | `Zhi-Hua-Yuan/LearningManage` |
| 后端基线 | `e5b247c7ed7d3dd8994c9af5ff76ce95d9d0c79b` |
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| 前端基线 | `cdff8f777843ab18f0c01c08d5f2ac7a82ec23e9` |
| 原有 operation | `37` |
| 原有 operation 合同 SHA-256 | `39CA49E63C1D1F3C6F7D232180F57B20A668B14573AC6C2792C65C4A53F69035` |
| PR7 新增前端 operation | `7` |
| PR7 预期 operation 总数 | `44`，以最终导出器结果为准 |

## 3. 交付物

1. [PR7 范围与开发计划](../frontend/pr7-scope-and-development-plan.md)
2. [PR7 API 与字段合同](../frontend/pr7-api-field-contract.md)
3. [PR7 UI 权限合同](../frontend/pr7-ui-permission-contract.md)
4. [PR7 周复盘隐私 UI 合同](../frontend/pr7-review-privacy-ui-contract.md)
5. [PR7 状态、缓存与错误合同](../frontend/pr7-state-cache-error-contract.md)
6. [PR7 测试矩阵](../frontend/pr7-test-matrix.md)
7. [PR7 机器可读验收合同](pr7-acceptance-contract.json)及其 [JSON Schema](pr7-acceptance-contract.schema.json)

## 4. 已冻结的关键决策

- 前端新增调用为 7 个；团队退出和成员移除 UI 不属于 PR7。
- 成员移除真实路径为 `POST /api/team/member/remove`，`teamId` 位于请求体中。
- 当前 `GET /api/task/list` 不支持 `assigneeUserId` 或 `assignmentScope`；团队任务页必须通过显式 `projectId` 查询，并以返回的 `TaskVO.assigneeUserId` 展示负责人。
- 现有任务的按钮和字段编辑只由 `TaskVO.capabilities` 驱动；能力缺失、加载失败或未知值均按拒绝处理。
- `TaskAssignRequest.expectedAssigneeUserId` 必须作为显式 JSON 属性发送；值为 `null` 表示预期当前未分配。
- 作者复盘与团队共享摘要使用不同前端类型；共享列表不得复用作者详情请求或持久化私人正文。
- 分配成功、并发冲突、401、登出及身份切换均有确定的缓存失效或清理顺序。
- 后端返回的 `completedTaskCount` 与 `focusProjectName` 为服务端鉴权后的权威值，前端不得用本地关联数据覆盖。

## 5. WP7-A 门禁结果

| 门禁 | 结果 | 说明 |
|---|---|---|
| 范围和非范围完整 | `PASS` | WP7-A～F 的职责、顺序和退出条件已固定 |
| API 路径与字段静态核对 | `PASS` | 7 个新增调用与当前 Controller/DTO 对齐；两处历史漂移已修正 |
| 37 operation 兼容基线固定 | `PASS` | 数量与 SHA-256 已写入机器合同 |
| 权限与隐私边界 | `PASS` | 能力映射、失败关闭、共享白名单和禁止详情回读已固定 |
| 缓存与错误处理 | `PASS` | `S1-R-013` 的实现与关闭证据要求已固定 |
| 测试矩阵 | `PASS` | 38 个分域编号场景已分配到后续工作包；编号覆盖 `PR7-T-001`～`PR7-T-045` 并保留领域间号段 |
| JSON Schema 与合同不变量 | `PASS` | Schema 校验通过；`37 + 7 = 44`；工作包和门禁 ID 唯一 |
| Markdown 相对链接 | `PASS` | WP7-A 新增/修改文档的本地相对链接均可解析 |
| 合并后 Backend CI | `PASS` | CI run `33353781042` 的 5 个必需 Job 全部成功 |
| 前端实现、全量测试和 44 operation 导出 | `PENDING` | 由 WP7-B～F 完成 |
| 受保护 PR、CI 与合并证据 | `PENDING` | 合并后补录 |

## 6. 风险状态

- `S1-R-010` 保持 `OPEN`：WP7-A 只固定兼容基线，关闭需要最终前端导出与运行时 OpenAPI 对比证据。
- `S1-R-013` 保持 `OPEN`：WP7-A 只固定失效规则，关闭需要缓存、401、登出和跨用户自动化测试证据。

## 7. 变更边界

WP7-A 只修改 `docs/stage1/**`。未修改 Java、Vue/TypeScript、数据库迁移、CI、运行配置或部署文件。

## 8. 后续准入

WP7-B 可以开始实现，但不得改变本记录已冻结的 operation 范围、字段白名单、权限失败关闭和隐私状态机。
