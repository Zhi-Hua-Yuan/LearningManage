# PR6 周复盘隐私与团队共享验收记录

日期：2026-08-28  
范围：周复盘 DTO/VO、PRIVATE/TEAM 可见性、团队共享摘要、项目/任务归属校验

## 1. 交付物

- `WeeklyReviewSaveRequest`：承载可选的 `visibilityScope`、`teamId`、`focusProjectId`、`sharedSummary`、`taskIds`。
- `WeeklyReviewVO`：作者完整视图，包含私人正文和关联任务 ID。
- `WeeklyReviewSharedVO`：团队共享视图，仅包含共享摘要和安全的周/项目摘要，不定义 `reflection`、`nextPlan` 或私有任务列表。
- `weekly_review_task` MyBatis 实体/Mapper 与事务内关联替换。
- `/api/review/team`：仅当前有效团队成员可查询指定团队的 TEAM 摘要。
- 保存/更新时校验团队成员身份、重点项目团队归属、关联任务团队归属；PRIVATE 切换会清除 `teamId`。
- 周统计的完成任务口径统一使用 `assignee_user_id`。

## 2. 本地验收

| Gate | 结果 | 证据 |
|---|---|---|
| TEAM 摘要非空 | PASS | `WeeklyReviewServiceImplTest.saveTeamReview_shouldRequireNonBlankSharedSummary` |
| 跨团队重点项目阻断 | PASS | `WeeklyReviewServiceImplTest.saveTeamReview_shouldRejectFocusProjectFromAnotherTeam` |
| TEAM → PRIVATE 清除共享目标 | PASS | `WeeklyReviewServiceImplTest.savePrivateReview_shouldClearTeamIdWhenSwitchingFromTeam` |
| 共享 VO 无私人字段 | PASS | `WeeklyReviewServiceImplTest.listTeamSharedReviews_shouldReturnSafeSharedViewOnly` |
| 作者完整视图含私人正文和任务关联 | PASS | `WeeklyReviewServiceImplTest.fullView_shouldIncludePrivateContentAndTaskLinks` |
| 全量回归 | PASS | Maven `130` tests, failures 0, errors 0, skipped 0 |
| `git diff --check` | PASS | 无尾随空白或补丁格式错误 |

## 3. 权限结论

- PRIVATE 周复盘完整视图继续由 `PermissionService.requireWeeklyReviewFullView` 保护，非作者不返回私人正文。
- TEAM 查询先校验当前有效成员，再返回独立共享 VO；`SYSTEM_ADMIN` 不获得默认内容读取旁路。
- 作者退出团队后仍可读取自己的完整复盘；若要继续保存该团队 TEAM 复盘，必须重新具备有效成员身份，或切换为 PRIVATE。

## 4. 受保护 CI

GitHub Actions 运行：

- [Run 33094880059](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33094880059)
- Maven verification and tested artifact：PASS（job 98597120896）
- Guard and migration immutability：PASS（job 98597050599）
- Flyway empty database gate：PASS（job 98597583194）
- Flyway existing database gate：PASS（job 98597904131）
- Docker runtime and migration gate：PASS（job 98598206382）

五项受保护门禁全部通过，PR6 具备进入评审/合并流程的验收证据。
