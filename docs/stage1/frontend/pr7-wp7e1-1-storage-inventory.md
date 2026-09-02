# WP7-E1-1.1 Storage Asset Inventory

状态：`COMPLETED（盘点基线）`

日期：2026-09-02

范围：仅盘点前端 `localStorage`、`sessionStorage`、缓存封装和敏感内存状态；本工作包不修改运行时缓存语义，不修改缓存 key，不接入 401/登出。

## 1. 扫描证据

扫描脚本：`learning-manage-frontend/scripts/scan-storage-assets.mjs`

执行命令：

```text
node scripts/scan-storage-assets.mjs --output-dir D:\ajavacode\LearningManage\docs\stage1\evidence\wp7-e1-1
```

产物：

- [wp7-e1-1-storage-scan.txt](../evidence/wp7-e1-1/wp7-e1-1-storage-scan.txt)
- [wp7-e1-1-storage-scan.json](../evidence/wp7-e1-1/wp7-e1-1-storage-scan.json)

扫描范围：

- `src/**/*.ts`
- `src/**/*.vue`
- `scripts/**/*.mjs`

扫描排除：

- `src/test/**`
- `*.test.ts`
- `*.spec.ts`
- 扫描脚本自身

本次可复现统计：

| 类型 | 数量 |
|---|---:|
| 直接 localStorage 调用 | 16 |
| 直接 sessionStorage 调用 | 4 |
| 缓存封装调用 | 36 |
| 扫描命中总数 | 56 |

## 2.1 验证结果

| 门禁 | 结果 |
|---|---|
| `node scripts/scan-storage-assets.mjs --output-dir ...` | PASS；56 条命中，重复运行统计一致 |
| `npm run lint:cache-views` | PASS |
| `npm run test:ci` | PASS；52 files / 384 tests |
| `npm run lint:ci` | PASS；oxlint 0 warnings / 0 errors，eslint PASS |
| `npm run type-check` | PASS |
| `npm run build` | PASS |
| `npm run contract:test` | PASS；3 subtests |
| `npm run contract:verify` | PASS；44 operations，sha256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |

## 2. 持久化资产清单

以下清单记录当前源码事实。`target` 只表示后续处理方向，不代表本工作包已经完成隔离或清理。

| Asset ID | 当前 key/模式 | 存储 | 来源 | 当前 payload/用途 | 当前风险 | target |
|---|---|---|---|---|---|---|
| S7-CACHE-001 | `token` | localStorage | `src/utils/authToken.ts` | 认证 token | 会话清理未统一接入 | E2 |
| S7-CACHE-002 | `tick_themeMode` | localStorage | `src/utils/appCache.ts` | 全局主题偏好 | 无账号隔离要求 | 保留全局偏好 |
| S7-CACHE-003 | `tick_sidebarWidth` | localStorage | `src/layout/BasicLayout.vue` | 侧栏宽度 | 页面直接访问 | 保留并登记偏好 |
| S7-CACHE-004 | `tick_detailWidth` | localStorage | `src/views/task/TaskList.vue` | 任务详情宽度 | 页面直接访问，已有局部 allowlist | 保留并登记偏好 |
| S7-CACHE-005 | `tick_selectedProjectId` | localStorage | `src/utils/appCache.ts` | 当前选中项目 ID | 未绑定 actor | E1-2 |
| S7-CACHE-006 | `tick:cache:project-list:status-{status}:v1` | localStorage | `src/utils/projectCache.ts` | 项目列表数组 | 未绑定 actor | E1-2 |
| S7-CACHE-007 | `tick:cache:project-progress:v2` | localStorage | `src/utils/projectCache.ts` | projectId 到进度的 map | 未绑定 actor | E1-2 |
| S7-CACHE-008 | `tick:cache:task-list:v1:{projectId}` | localStorage | `src/utils/taskCache.ts` | 项目任务数组 | 只有 projectId，无 actorId | E1-2 |
| S7-CACHE-009 | `tick:cache:task-list:all:v1` | localStorage | `src/utils/taskCache.ts` | 聚合任务 map | 全局共享，高风险 | E1-2 |
| S7-CACHE-010 | `tick_aiPlannerDraft_v1` | localStorage | `src/utils/appCache.ts` | AI planner 草稿 | 可能含业务上下文 | E1-2/E2 |
| S7-CACHE-011 | `tick:cache:task-today-ai-order:v1` | localStorage | `src/utils/appCache.ts` | 今日 AI 排序状态 | 可能跨账号复用任务上下文 | E1-2 |
| S7-CACHE-012 | `tick:cache:task-list-replan-state:v1` | localStorage | `src/utils/appCache.ts` | 任务重排状态 | 可能跨账号复用项目上下文 | E1-2 |
| S7-CACHE-013 | `tick_backend_cache_version` | localStorage | `src/utils/cacheVersion.ts` | 后端缓存版本 | 当前版本清理按全 tick 前缀处理 | E1-3 |
| S7-CACHE-014 | `tick_backend_cache_reload_lock` | sessionStorage | `src/utils/cacheVersion.ts` | 版本 reload 锁 | 基础设施元数据 | 保留独立管理 |
| S7-CACHE-015 | `ai:draft:confirm-operation:{draftId}` | sessionStorage | `src/views/ai/AiDraftDetail.vue` | AI 草稿确认 operationId | 含业务 draftId，会话结束需清理 | E2 |

## 3. 敏感内存资产清单

| Asset ID | 状态 | 来源 | 当前策略 | 后续关注点 |
|---|---|---|---|---|
| S7-MEM-001 | 当前用户 | `src/stores/collaboration.ts` | 会话内存 | E2 reset |
| S7-MEM-002 | 团队与角色 | `src/stores/collaboration.ts` | 会话内存 | E2 reset |
| S7-MEM-003 | 团队项目 | `src/stores/collaboration.ts` | 按 teamId 内存 | E2/E3 迟到响应 |
| S7-MEM-004 | 团队成员 | `src/stores/collaboration.ts` | 按需内存加载 | 禁止持久化 |
| S7-MEM-005 | Task capabilities | TaskModel 页面状态 | 服务端响应事实 | 禁止跨账号复用 |
| S7-MEM-006 | 负责人历史 | `useTaskAssignmentHistory` | 面板内存 | E2 reset |
| S7-MEM-007 | 团队共享复盘 | `useTeamSharedReviews` | 页面内存 | 禁止持久化 |
| S7-MEM-008 | PRIVATE 复盘正文/计划 | `WeeklyReview.vue` | 页面表单内存 | 禁止持久化 |
| S7-MEM-009 | AI requestMeta | `aiPendingRegistry` | 会话内存 | 含业务 ID，E2 reset |
| S7-MEM-010 | AI responsePayload | `aiPendingRegistry` | 会话内存 | 禁止日志和持久化 |
| S7-MEM-011 | 负责人选择与 reason | Task assignment dialog | 对话框内存 | 禁止日志和持久化 |

## 4. 已确认的差距

1. 个人项目、任务、聚合任务和项目进度缓存尚未绑定 actor。
2. `tick_selectedProjectId` 是单一全局 key，可能恢复到另一账号不可访问的项目。
3. `AiDraftDetail.vue` 的 sessionStorage operationId 没有统一会话清理入口。
4. `cacheVersion.ts` 当前按 `tick_`/`tick:` 前缀清理，尚未区分业务资源、UI 偏好和基础设施元数据。
5. 现有 `check-view-localstorage.mjs` 只覆盖 views 和 localStorage，不能证明全源码 storage 使用完整登记。
6. 团队成员、负责人历史、共享复盘和 AI pending 属于必须保持内存态的敏感资产，当前清单已补齐，但最终运行时门禁留给 E1-2/E2。

## 5. E1-1.1 结论

本次盘点已经覆盖生产源码中的直接 storage、缓存 helper、动态 key 和敏感内存状态，扫描结果可重复生成，未发现未登记的资产类别。

本工作包不关闭以下风险和门禁：

- `PR7-T-042`：需要后续运行态不落盘证据；
- `PR7-T-043`：需要 E2 接入 logout/401 清理；
- `PR7-T-044`：需要 E1-2/E3 的 actor 隔离和迟到响应测试；
- `S1-R-013`：继续保持 `OPEN`。

下一主目标：`WP7-E1-2 资产分类与 scope 冻结`。
