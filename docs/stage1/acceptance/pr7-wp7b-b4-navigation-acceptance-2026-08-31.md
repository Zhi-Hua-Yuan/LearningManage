# PR7 / WP7-B4 团队项目导航与路由恢复验收记录

状态：`IMPLEMENTED / LOCAL_VALIDATION_PASS`

日期：2026-08-31

## 1. 验收结论

WP7-B4 已完成本地实现与门禁验证。前端已将 B3 collaboration Store 接入布局、团队项目导航和任务页路由，支持团队项目懒加载、分页、深链接恢复、失权回退和团队任务缓存隔离。

B4 未新增或修改 API operation；团队项目和协作上下文仍只保存在 Pinia 内存中。成员加载、任务 capabilities、负责人编辑、周复盘隐私界面继续留给后续工作包。

## 2. 基线与交付物

| 项目 | 值 |
|---|---|
| 前端 B3 基线 | `6bc40a0` |
| 后端文档基线 | `3abc696` |
| API operations | `44` |
| API 合同 SHA-256 | `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |

主要交付物：

- `src/router/taskProjectContext.ts`
- `src/router/taskProjectContext.test.ts`
- `src/components/navigation/TeamProjectNavigation.vue`
- `src/components/navigation/teamNavigation.ts`
- `src/components/navigation/teamNavigation.test.ts`
- `src/layout/BasicLayout.vue`
- `src/views/task/TaskList.vue`
- `scripts/check-task-cache-consistency.mjs`

## 3. 功能与安全边界

- 侧边栏按“个人项目 → 团队 → 团队项目”展示层级；团队项目按展开懒加载并支持继续分页；
- 团队项目路由使用 `teamId + projectId`，个人项目路由只使用 `projectId`；
- 直接访问团队项目深链接时调用 `restoreTeamProjectContext`，目标团队自动展开；
- 团队不存在、项目不存在或上下文非法时使用 `router.replace` 回退到仍可访问的个人项目；
- 临时网络错误保留当前深链接并进入可重试状态，不误判为失权；
- 团队项目不会写入个人项目选择缓存；
- 团队任务不会读取或写入无团队维度的持久任务缓存，也不会更新个人聚合任务缓存；
- 团队任务不触发成员加载，不在 B4 自行推导角色到任务 capability；
- 个人今日/本周聚合视图仍保持个人项目范围；
- 桌面和移动侧边栏共用同一导航状态，移动端选择项目后关闭侧栏；
- 401 清理和 403/404 团队裁剪继续由 B3 Store 负责，B4 只消费恢复结果。

## 4. 自动化测试证据

新增路由上下文和导航模型测试，覆盖：

- 个人、团队、聚合和非法路由解析；
- 团队项目路由构建和个人回退选择；
- 角色文案和展开状态不可变切换；
- 路由上下文和个人回退逻辑的纯函数行为；
- 团队角色文案和展开状态的纯函数行为；
- B3 Store 继续通过原有 19 个聚焦测试；
- 团队任务持久缓存隔离通过 TypeScript、Lint、构建和代码审查门禁确认。

## 5. 门禁证据

| 门禁 | 结果 | 证据 |
|---|---|---|
| TypeScript 类型检查 | `PASS` | `npm run type-check` |
| 全量 Vitest | `PASS` | 12 files / 85 tests passed |
| 覆盖率 | `PASS` | Statements 68.87%；Branches 58.87%；Functions 60.06%；Lines 72.86% |
| 完整 Lint | `PASS` | view localStorage、task cache consistency、Oxlint、ESLint 全部通过 |
| 生产构建 | `PASS` | Vite production build 通过 |
| API 合同测试 | `PASS` | 3 subtests passed |
| API 合同校验 | `PASS` | 44 operations；SHA-256 未变化 |
| Diff 检查 | `PASS` | `git diff --check` 无空白错误 |

## 6. 后续准入

WP7-B4 已完成本地实现，WP7-B 可进入最终 PR/CI 收口。合并前仍需在目标分支重新执行相同门禁并补充远端 CI 运行号；WP7-C 再接入任务 capabilities 和负责人交互，WP7-E 负责最终缓存与会话治理收口。
