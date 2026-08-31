# PR7 / WP7-B3 协作上下文 Store 验收记录

状态：`IMPLEMENTED / LOCAL_VALIDATION_PASS`

日期：2026-08-31

## 1. 验收结论

WP7-B3 已完成。前端已建立当前用户、团队、团队项目和团队成员的会话级内存 Store，并实现懒加载、分页、请求去重、团队失权恢复、账号切换隔离和迟到响应防污染。

B3 未修改页面、侧边栏或路由，未新增 API operation，未将协作上下文写入 `localStorage` 或 `sessionStorage`。路由接线和失权后的实际导航由后续 B4 完成；跨任务、复盘和 AI 状态的全局会话清理由 WP7-E 最终收口。

## 2. 基线与变更

| 项目 | 值 |
|---|---|
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| B1 提交 | `860b017` |
| B2 提交 | `753dc45` |
| B3 提交 | `6bc40a0` |
| 当前 operation | `44` |
| API 合同 SHA-256 | `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |

主要交付物：

- `src/stores/collaboration.ts`
- `src/stores/collaboration.test.ts`

## 3. Store 能力

`useCollaborationStore` 提供以下入口：

- `bootstrapCollaborationContext`：先读取当前用户，再读取我的团队；
- `refreshMyTeams`：以 `/team/my` 为权威来源并裁剪失效团队状态；
- `ensureTeamProjects`：按 `teamId` 懒加载团队项目；
- `loadMoreTeamProjects`：使用 `pageNum/pageSize` 追加分页并按 ID 去重；
- `ensureTeamMembers`：按 `teamId` 懒加载并完整刷新有效成员；
- `restoreTeamProjectContext`：为后续路由恢复返回显式判别结果，不直接依赖 Router；
- `invalidateTeamProjects` / `invalidateTeamMembers`：为后续任务写操作和缓存治理提供失效入口；
- `pruneTeamContext` / `clearCollaborationContext`：清理团队级或会话级协作状态。

应用初始化不会遍历团队加载项目或成员。团队项目首屏使用 `pageSize=100`；直接恢复深链接时仅对目标团队继续分页，直至找到目标项目或确认项目不存在。

## 4. 安全与恢复边界

- B1 的 ID、用户、团队、成员、项目标准化函数被统一复用；
- 非法 ID 和未知团队在发送资源请求前被拒绝；
- 跨团队项目、非法项目和非法成员不会写入 Store；
- 未知团队角色保持 `UNKNOWN`，Store 不推导任务 capability；
- 团队成员和项目只保存在 Pinia 会话内存中；
- `40100` / HTTP 401 清理全部 collaboration Store；
- `40300` / `40101` / `40400` 不清理登录状态，而是裁剪目标团队资源并重新读取 `/team/my`；
- 网络或服务端临时失败保留最后一次成功数据，并记录可重试错误状态；
- `/team/my` 不再返回某团队时，其项目、成员和未完成请求状态一并删除；
- 用户身份变化时增加 `sessionEpoch` 并清理旧账号协作数据；
- 项目和成员各自使用资源 revision，已失效请求的迟到响应不能恢复旧数据；
- Store 不记录完整 API 响应、Token、私人复盘正文或共享摘要。

## 5. 自动化测试证据

B3 新增 19 个 Store 聚焦测试，覆盖：

- 当前用户到团队的顺序初始化；
- 初始化无项目/成员扇出请求；
- 初始化和同团队资源并发去重；
- 团队、项目和成员标准化与去重；
- 团队项目分页、跨团队结果过滤和深链接恢复；
- 成员按需加载和离队成员刷新删除；
- 非法/失权上下文在网络请求前或返回后安全失败；
- 团队消失后的项目和成员裁剪；
- 用户 A 切换用户 B 的上下文隔离；
- 上一账号和已失效资源的迟到响应丢弃；
- 403 重新确认团队但不清理当前用户；
- 401 清理 collaboration Store；
- 临时失败保留最后成功数据；
- 不写浏览器持久存储。

## 6. 门禁证据

| 门禁 | 结果 | 证据 |
|---|---|---|
| TypeScript 类型检查 | `PASS` | `npm run type-check` |
| B3 Store 聚焦测试 | `PASS` | 1 file / 19 tests passed |
| 全量 Vitest + Coverage | `PASS` | 10 files / 79 tests passed |
| B3 Store 覆盖率 | `PASS` | statements 84.81%；branches 67.02%；functions 93.93%；lines 87.94% |
| 完整 Lint | `PASS` | localStorage guard、task cache consistency、Oxlint、ESLint 全部通过 |
| 生产构建 | `PASS` | TypeScript 与 Vite production build 通过 |
| API 合同测试 | `PASS` | 3 subtests passed |
| API 合同校验 | `PASS` | 44 operations；SHA-256 未变化 |

## 7. 后续准入

WP7-B3 已完成，WP7-B 继续执行。下一步进入 B4，将 collaboration Store 接入布局、团队项目导航和路由恢复；B4 不应在页面内重复实现团队项目/成员加载、请求去重或失权裁剪逻辑。
