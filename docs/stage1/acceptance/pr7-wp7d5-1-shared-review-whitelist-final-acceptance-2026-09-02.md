# PR7 / WP7-D5-1：团队共享复盘运行时白名单最终验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-02

## 1. 验收范围

本记录覆盖 WP7-D5-1 的团队共享复盘类型隔离、运行时白名单投影和分页归一化：

- `SharedWeeklyReviewWire` 与作者完整复盘类型保持独立，不继承作者详情类型；
- 共享记录只允许 `id`、作者摘要、周信息、重点项目摘要、共享摘要和时间字段；
- `reflection`、`nextPlan`、`taskIds`、`completedTaskCount`、`visibilityScope`、`teamId` 及私人项目详情不会进入共享状态；
- 新增共享分页归一化，逐条执行白名单投影，缺少合法 ID 的记录被丢弃；
- 归一化不会读取或条件探测禁止的私人字段，不修改原始 Wire 响应；
- 不改变 `/review/team` API operation、分页请求范围或 44 operation 合同；
- 不包含 WP7-D5-2 列表状态机、WP7-D5-3 共享卡片和 WP7-E 全局缓存治理。

冻结依据：

- [PR7 周复盘隐私界面合同](../frontend/pr7-review-privacy-ui-contract.md)
- [PR7 测试与验收矩阵](../frontend/pr7-test-matrix.md)

## 2. 前端受保护合并证据

| 子项 | 证据 |
|---|---|
| 前端 PR | [#34](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/34) |
| 分支 | `codex/wp7-d5-1-shared-review-whitelist` → `develop` |
| Head SHA | `b40b8be5b19c4861a7ea75e8eaea560cc43ddf24` |
| Merge SHA | `0e97b083a95a9f544d3276b0ff87daff35793ef8` |
| PR CI | [33610139834](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33610139834)：`SUCCESS` |
| develop post-merge CI | [33610591225](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33610591225)：`SUCCESS` |

PR CI 的 Guard and secret scan、Frontend tests and static verification、Frontend production build 三项检查全部成功。合并后的 `develop` post-merge CI 同样包含三项 job，全部成功；post-merge HEAD 为 `0e97b083a95a9f544d3276b0ff87daff35793ef8`，与 Merge SHA 一致。

## 3. 实现结果

### 3.1 共享白名单投影

- `normalizeSharedWeeklyReviewWire()` 继续采用显式字段构造，不使用对象展开或原始响应透传；
- 顶层输出固定为 `author`、`createTime`、`endDate`、`focusProject`、`id`、`sharedSummary`、`startDate`、`updateTime`、`weekNo`、`year`；
- `author` 仅输出 `id`、`username`；
- `focusProject` 仅输出 `id`、`name`；
- 注入额外私人字段时，归一化结果仍不包含这些字段；
- 使用抛错 getter 验证时，禁止字段和私人项目详情均未被读取。

### 3.2 共享分页归一化

- 新增 `normalizeSharedWeeklyReviewPage()`；
- 复用通用分页安全默认值：`current=1`、`size=20`、`total=0`；
- 分页中的非法记录不会进入 `PageResult<SharedWeeklyReview>`；
- 归一化结果与 Wire 及其嵌套对象均为独立对象；
- 不在 D5-1 提前承担跨页去重、请求竞态或团队失权清理。

核心实现与测试：

- [normalization.ts](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/blob/0e97b083a95a9f544d3276b0ff87daff35793ef8/src/types/normalization.ts)
- [sharedWeeklyReviewNormalization.test.ts](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/blob/0e97b083a95a9f544d3276b0ff87daff35793ef8/src/types/sharedWeeklyReviewNormalization.test.ts)

## 4. 自动化验收结果

### D5-1 聚焦门禁

- 2 个测试文件；
- 21 个测试；
- 0 failures；
- 覆盖顶层和嵌套字段白名单、私人字段探测防护、对象隔离、非法 ID 丢弃及分页默认值。

### 前端全量门禁

| 门禁 | 结果 |
|---|---|
| `npm run test:ci` | 48 files / 353 tests / 0 failures |
| `npm run test:coverage` | PASS |
| Statements | 83.44% |
| Branches | 74.71% |
| Functions | 83.39% |
| Lines | 87.22% |
| `npm run type-check` | PASS |
| `npm run lint:ci` | PASS，0 warnings / 0 errors |
| `npm run build` | PASS |
| `npm run contract:test` | PASS |
| `npm run contract:verify` | PASS |
| `git diff --check` | PASS |

## 5. API 合同不变性

```text
operations: 44
sha256: 4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6
```

本次没有新增 operation，也没有修改既有 operation 的 method、path 或必填字段。

## 6. 风险与阶段边界

- D5-1 关闭共享运行时对象误携带私人字段的前端类型/归一化风险；
- 团队动态列表、分页请求状态、团队切换和 403/404 清理属于 WP7-D5-2；
- 共享卡片只读 UI 和纯文本渲染属于 WP7-D5-3；
- 全局缓存、401、登出和多账号隔离仍属于 WP7-E；
- `S1-R-013` 继续保持 `OPEN`，D5-1 不构成其关闭证据。

## 7. 结论

WP7-D5-1 的实现、自动化测试、受保护合并和合并后 CI 均已完成，可标记为：

```text
WP7-D5-1：PASS / COMPLETED / MERGED / CI_PASS
```

WP7-D 整体继续保持 `IN_PROGRESS`，下一主目标为 WP7-D5-2。
