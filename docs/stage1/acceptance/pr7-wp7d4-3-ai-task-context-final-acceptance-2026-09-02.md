# PR7 / WP7-D4-3：AI 周复盘润色任务上下文最终验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-02

## 1. 验收范围

本记录覆盖 WP7-D4-3 的 AI 周复盘润色任务来源治理与竞态保护：

- 作者显式关联的 `taskIds` 优先作为 AI 请求上下文，去重后按稳定顺序发送；
- 显式任务 ID 中存在非法值时失败关闭，不混入 fallback 候选；
- 未提供显式任务时，仅回退到当前用户在本周内完成且属于完整快照的任务；
- fallback 使用 `completedAt` 和当前用户 `assigneeUserId` 判定，不再使用不稳定的旧候选缓存或仅按截止日期推断；
- AI 授权失败、任务不存在或参数校验失败时刷新候选上下文，不对原请求做部分 ID 重试；
- 周复盘、用户会话或页面实例变化后，迟到的 AI 响应不得回写新的编辑上下文；
- AI 返回空值或非法 JSON 时保留原始复盘正文，不覆盖用户内容；
- 不改变后端 API、数据库和 44 operation 合同。

D4-3 不处理团队共享白名单卡片（D5）或全局缓存、401、登出和多账号隔离（WP7-E）。

## 2. 前端受保护合并证据

| 子项 | 证据 |
|---|---|
| 前端 PR | [#33](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/33) |
| 分支 | `codex/wp7-d4-3-ai-task-context` → `develop` |
| Head SHA | `2f34f508c9b63fbbe258c2c6374ef0e33559031b` |
| Merge SHA | `863fb22313c082631f9a67362c0f27de612c193d` |
| PR CI | [33603426591](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33603426591)：`SUCCESS` |
| develop post-merge CI | [33603612513](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33603612513)：`SUCCESS` |

合并后的 `develop` HEAD 为 `863fb22313c082631f9a67362c0f27de612c193d`，与 PR Merge SHA 完全匹配。post-merge Frontend CI 的 Guard and secret scan、Frontend tests and static verification、Frontend production build 三项 job 全部成功。

## 3. 后端证据合并门禁

| 子项 | 证据 |
|---|---|
| 后端证据 PR | [#80](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/80) |
| Head SHA | `b96983a8042b02ab8065f1641faa4248d11dc432` |
| Merge SHA | `4d2a16b51d023840ea38f963cc4e6363ade11bcc` |
| PR CI | [33605140132](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33605140132)：`SUCCESS` |
| develop post-merge CI | [33605710000](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33605710000)：`SUCCESS` |

后端 PR #80 仅包含本验收记录、合并收口记录、阶段总览和风险登记；Guard、Maven、Flyway 空库、Flyway 现有库及 Docker runtime 五项门禁全部成功。

## 4. 实现结果

### 4.1 任务上下文解析

- 新增统一的周复盘 AI 上下文解析器，明确区分显式关联来源与 fallback 来源；
- 显式 ID 归一化、去重并保持输入顺序；任何显式非法 ID 均拒绝请求，避免“合法部分 + 隐式候选”造成语义漂移；
- fallback 要求任务快照完整、当前用户身份有效、任务已完成且 `completedAt` 落在复盘周范围内；
- 缺少完整快照时返回空候选并失败关闭，不以部分分页数据代表完整周任务集合。

### 4.2 错误、竞态与正文保护

- AI 授权、资源不存在和参数校验错误会触发候选刷新，但不会对同一请求执行逐条或部分 ID 重试；
- 请求元数据绑定页面实例、复盘键、用户身份和上下文 revision；上下文变化后丢弃旧响应；
- 非法、空或不可解析的 AI 响应不会覆盖已有 reflection 草稿，用户正文保持可恢复。

### 4.3 API 与合同边界

- 继续复用 `POST /api/ai/polish`，请求只携带经过前端上下文治理的 `taskIds` 与 reflection；
- 未新增或修改 operation、method、path 和必填字段；
- D4-3 仅调整前端任务来源、状态和响应防护，不引入数据库迁移或后端业务代码变更。

## 5. 自动化验收结果

### D4-3 聚焦门禁

- 3 个聚焦测试文件；
- 26 个测试；
- 0 failures；
- 覆盖显式任务优先、非法 ID 失败关闭、当前用户周内完成任务 fallback、授权失败刷新且不部分重试、迟到响应丢弃和 malformed response 正文保留。

### 前端全量门禁

| 门禁 | 结果 |
|---|---|
| `npm run test:ci` | 47 files / 347 tests / 0 failures |
| `npm run test:coverage` | PASS |
| Statements | 83.36% |
| Branches | 74.15% |
| Functions | 83.36% |
| Lines | 87.19% |
| `npm run type-check` | PASS |
| `npm run lint:ci` | PASS，0 warnings / 0 errors |
| `npm run build` | PASS |
| `npm run contract:test` | PASS |
| `npm run contract:verify` | PASS |
| `git diff --check` | PASS |

## 6. API 合同不变性

```text
operations: 44
sha256: 4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6
```

本次没有新增 operation，也没有修改既有 operation 的 method、path 或必填字段。

## 7. 风险与阶段边界

- D4-3 已覆盖 AI 周复盘任务来源、授权错误刷新和 stale response 防护；
- 团队共享白名单卡片与共享视图仍属于 WP7-D5；
- 全局缓存、401、登出和多账号隔离仍属于 WP7-E；
- `S1-R-013` 继续保持 `OPEN`，待 WP7-E 完成跨页面和跨会话清理证据后再关闭。

## 8. 结论

WP7-D4-3 的实现、测试、受保护合并和合并后 CI 均已完成，可标记为：

```text
WP7-D4-3：PASS / COMPLETED / MERGED / CI_PASS
```

WP7-D 整体仍保持 `IN_PROGRESS`，下一主目标切换为 WP7-D5。
