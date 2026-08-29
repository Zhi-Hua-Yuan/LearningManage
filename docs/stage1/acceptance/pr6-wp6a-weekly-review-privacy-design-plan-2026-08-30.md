# PR6 WP6-A：周复盘隐私与统计/AI 授权设计计划

状态：`IN_PROGRESS（设计冻结候选）`

日期：2026-08-30

## 1. 目标与边界

PR5 已在 `develop` 完成合并收口。PR6 的第一工作包先冻结周复盘隐私、关联校验、统计口径和现有 AI 入口授权的实现合同，再进入后端代码开发。

本工作包只处理后端业务语义和验收设计，不修改已发布 V1/V2 Flyway 文件，不接入前端、Qdrant、RAG、Agent 或新的 AI 模型协议。

## 2. 权威输入

- [阶段 1 需求合同](../requirements/stage1-requirements-contract.md)：S1-F-006、S1-F-007、S1-F-008、S1-F-012；
- [ADR-002：周复盘可见性与团队共享](../architecture/ADR-002-weekly-review-visibility.md)；
- [API 兼容合同](../api/api-compatibility-contract.md)第 2、4、5 节；
- [权限矩阵](../authorization/permission-matrix.md)中的周复盘和 AI 场景；
- PR5 合并后的 `develop=b1d5a262908a2b91f2f0eaa1e097b2499488afc3`。

## 3. 冻结的业务合同

### 3.1 周复盘可见性

1. `/review/current`、`/review/save`、`/review/update`、`/review/{id}`、`/review/history` 保留原 method + path。
2. 请求/响应改用 DTO/VO，不再直接暴露 `WeeklyReview` 实体。
3. 未传 `visibilityScope` 时按 `PRIVATE` 处理。
4. 作者读取自己的完整 VO；非作者只能在当前有效团队成员关系下读取指定团队的 `WeeklyReviewSharedVO`。
5. `WeeklyReviewSharedVO` 不定义 `reflection`、`nextPlan`、私人任务列表或私人项目详情。
6. `TEAM` 必须同时满足：`teamId` 非空、`sharedSummary` 非空、作者当前属于该团队、重点项目和所有任务均属于该团队。
7. `PRIVATE` 必须满足 `teamId=NULL`；改为 PRIVATE 或删除后，团队共享查询立即不可见。
8. 作者退出团队后仍可读取自己的完整复盘，但不能继续保存面向该团队的 `TEAM` 复盘。

### 3.2 关联与事务

- 保存/更新在一个应用事务中完成：校验可见性、团队、项目和任务后写入复盘及 `weekly_review_task` 关联；
- 关联资源按批量权限查询校验，禁止逐资源 N+1；
- 读取详情和历史列表重新执行当前权限判断，不信任保存时权限；
- 存量复盘保持 PRIVATE 语义，不能因读取或更新自动生成共享摘要。

### 3.3 统计与 AI 授权

- `completedTaskCount` 按 `task.assignee_user_id` 和完成时间计算；
- `focusProject` 按用户实际受理并完成的任务计算，不再使用 `task.user_id` 作为执行者口径；
- 周复盘润色、草稿确认和现有 AI 入口中的 `projectId/taskId` 在模型调用前统一调用 `PermissionService`；
- 请求中出现不存在或越权 ID 时整体拒绝，不部分放行、不调用模型；
- 本工作包不拆分 `AiServiceImpl`，不改变模型协议、Prompt 或降级管线。

## 4. 开发工作包

| 工作包 | 内容 | 交付物 |
|---|---|---|
| WP6-B | 周复盘 Request/Response DTO、PRIVATE/TEAM 保存与读取、共享查询接口 | Java 实现、序列化合同测试 |
| WP6-C | 项目/任务/团队批量关联校验、事务边界和历史读取权限 | Service/Mapper 实现、越权与跨团队测试 |
| WP6-D | 统计口径修正、现有 AI ID 授权回归 | SQL/Service 修正、AI 授权测试 |
| WP6-E | 集成验收、风险关闭和证据封存 | MySQL/权限/回归报告，更新 S1-A-006/S1-A-008 |

WP6-A 本身不宣称任何功能验收通过；只有 WP6-B～E 的实现、测试和真实数据库证据完成后，才可更新机器合同状态。

## 5. 验收门槛

- 非作者读取私人 `reflection`/`nextPlan` 的返回数为 0；
- TEAM 共享 VO 的序列化字段不包含私人正文；
- 缺失团队、空共享摘要、跨团队项目/任务均拒绝落库；
- 作者退出后不能继续保存 TEAM，但仍能读取自己的完整 PRIVATE/历史复盘；
- 关联校验无逐资源 N+1；
- AI 请求含不存在或越权业务 ID 时模型调用次数为 0；
- 统计完成数与重点项目使用 `assignee_user_id` 的回归用例通过；
- 原有个人 PRIVATE 周复盘接口保持兼容；
- 不修改已发布 Flyway 文件，不引入前端或 RAG/Agent 代码。

## 6. 风险与后续状态

- `S1-A-006`、`S1-A-008` 在实现和真实验收前保持 `PENDING`；
- `S1-R-004`、`S1-R-008`、`S1-R-012` 继续 `OPEN`，分别由 WP6-C/D/E 关闭；
- PR6 完成前不得启动 PR7 前端工作，也不得进入 RAG/Agent 阶段；
- 每个实现小 PR 必须从本设计合同派生，若改变共享语义需先新增 ADR 或修订 API 合同。

