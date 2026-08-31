# PR7 / WP7-C3 初始负责人选择验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-01

## 1. 验收结论

WP7-C3 已完成并完成前端受保护合并。本工作包实现了团队成员候选懒加载、负责人选择器、个人/团队项目负责人语义、团队创建入口角色门禁和创建任务时的初始 `assigneeUserId` 提交。

`/task/assign`、CAS 冲突、负责人变更历史和全局缓存治理不属于 C3，继续由 WP7-C4、C5 和 WP7-E 负责。WP7-C 整体仍保持 `PENDING`。

## 2. 前端合并证据

| 项目 | 结果 |
|---|---|
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| 前端分支 | `pr7-c3-assignee-picker` |
| PR | [#20](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/20) |
| PR head 提交 | `810dd0c58364d571d892123a1c7b22b9d2d6a134` |
| merge SHA | `3f887e684d1fc763744f15fa8baa2d31dd29a035` |
| PR CI run | [33418477858](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33418477858) |
| post-merge CI run | [33418753252](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33418753252) |
| post-merge CI 结果 | `PASS` |

## 3. C3 交付范围

- 新增受控 `TaskAssigneePicker`，支持鼠标、键盘、焦点恢复、加载、错误和重试状态；
- 团队成员只在打开选择器时通过 `ensureTeamMembers(teamId, { force: true })` 加载；
- 通过请求版本和团队上下文键丢弃切换团队后的迟到响应；
- 强制刷新失败时不展示或提交旧成员记录；
- 团队候选包含“未分配”，过滤其他团队、重复 userId，并对空 username 使用 `用户 #ID` 降级；
- 个人项目只展示当前用户，不加载团队成员；
- 团队 OWNER/ADMIN 允许快速创建，MEMBER/UNKNOWN 默认拒绝；
- 团队创建明确提交 `assigneeUserId`，包括未分配时的 `null`；
- 个人创建不携带 `assigneeUserId`；
- 创建期间冻结控件，创建失败保留草稿，成功后清理负责人草稿；
- 路由、团队和当前用户上下文变化时清理负责人选择状态。

## 4. 本地门禁

| 门禁 | 结果 |
|---|---|
| `npm run test:ci` | `25 files / 160 tests PASS` |
| `npm run test:coverage` | `76.31% statements / 64.23% branches / 73.25% functions / 80.34% lines` |
| `npm run type-check` | `PASS` |
| `npm run lint:ci` | `0 warnings / 0 errors` |
| `npm run contract:test` | `3/3 PASS` |
| `npm run contract:verify` | `44 operations PASS` |
| `npm run build` | `PASS` |
| `git diff --check` | `PASS` |

## 5. 合同与边界

- 前端 operation 数量保持 `44`；
- 合同 SHA-256 保持为 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6`；
- 未新增后端 API、数据库迁移或前端 operation；
- PR7-T-020（最新有效团队成员和未分配选项）已由 composable、候选生成和页面集成测试覆盖；
- PR7-T-010～017 的 capability 基础由 C1/C2 负责；T-021～027 的分配 CAS、历史和状态重试由后续 C4/C5 负责；
- `S1-R-013` 继续保持开放，等待 WP7-E 的全局缓存、会话和跨页面回归验收。

## 6. 后续目标

WP7-C3 关闭后，下一主目标切换为 WP7-C4：`/task/assign` 负责人分配、转派、解除及 CAS 冲突交互。WP7-C、WP7-D、WP7-E 和 WP7-F 仍按各自合同继续执行。
