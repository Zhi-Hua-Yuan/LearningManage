# PR7 / WP7-D4-2：周复盘服务端统计事实最终验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-02

## 1. 验收范围

本记录覆盖 WP7-D4-2 的周复盘统计事实保护：

- `completedTaskCount` 和 `focusProjectName` 始终以作者接口返回值为准；
- 客户端辅助完成率只统计当前用户负责的可读任务；
- 项目或任务快照不完整时，辅助指标失败关闭，不以部分数据计算结果冒充完整统计；
- 当前及历史周的完成任务变化使用服务端复盘统计，支持跨年周次；
- 统计请求绑定复盘、用户、会话和团队快照，迟到响应不得回写新上下文；
- 作者权威详情加载不依赖团队上下文初始化；
- 非法日历日期失败关闭，避免日期自动归一化污染周统计。

D4-2 不处理 AI 任务来源治理（D4-3）、团队共享卡片（D5）或全局会话缓存清理（WP7-E）。不改变后端 API、数据库和 44 operation 合同。

## 2. 前端受保护合并证据

| 子项 | 证据 |
|---|---|
| 前端 PR | [#32](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/32) |
| Head SHA | `dce64caffe37722feca653a64d119ed8525de39c` |
| Merge SHA | `c6bb5cbdbd820e5227279e893b9a8a3447191180` |
| PR CI | [33587108792](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33587108792)：`SUCCESS` |
| develop post-merge CI | [33587275741](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33587275741)：`SUCCESS` |

合并后 Frontend CI 的 Guard and secret scan、Frontend tests and static verification、Frontend production build 三项 job 全部成功，develop push 的 head SHA 与 Merge SHA 完全匹配。

## 3. 实现结果

### 3.1 服务端事实边界

- “本周完成任务”卡片读取作者详情中的 `completedTaskCount`；
- “核心推进项目”读取作者详情中的 `focusProjectName`；
- 辅助任务快照、完成率计算和项目刷新均不得修改作者详情；
- 上周完成数和完成任务变化优先使用精确上周复盘的服务端统计。

### 3.2 辅助指标与权限

- 辅助完成率仅纳入 `assigneeUserId === currentUserId` 的任务；
- 未分配任务、其他成员任务和当前用户上下文缺失的任务不会被计入；
- 项目或任务分页元数据不完整、权限上下文失效或快照请求失败时，辅助完成率显示占位状态；
- 服务端作者事实仍然保持可展示，不因辅助任务加载失败而清零或隐藏。

### 3.3 请求竞态与日期保护

- 统计请求绑定当前复盘键、用户 ID、会话 epoch 和团队快照；
- 切换历史复盘或会话上下文后，迟到的旧响应被丢弃；
- 非法日期（包括非法时间戳中的日期前缀）不会被 JavaScript 自动滚动到其他日期。

## 4. 自动化验收结果

### D4-2 聚焦门禁

- 3 个聚焦测试文件；
- 15 个测试；
- 0 failures；
- 覆盖服务端统计不覆盖、当前负责人过滤、完整快照失败关闭、竞态保护、跨年周和非法日期。

### 前端全量门禁

| 门禁 | 结果 |
|---|---|
| `npm run test:ci` | 46 files / 334 tests / 0 failures |
| `npm run test:coverage` | PASS |
| Statements | 82.23% |
| Branches | 72.60% |
| Functions | 82.38% |
| Lines | 86.03% |
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

- AI 显式任务优先、fallback 负责人过滤和权限错误治理属于 D4-3；
- 团队共享白名单卡片属于 D5；
- 全局 401、登出和多账号缓存隔离属于 WP7-E；
- `S1-R-013` 继续保持 `OPEN`。

## 7. 结论

WP7-D4-2 的实现、测试、受保护合并和合并后 CI 均已完成，可标记为：

```text
WP7-D4-2：PASS / COMPLETED / MERGED / CI_PASS
```

WP7-D 整体仍保持 `IN_PROGRESS`，下一主目标为 WP7-D4-3。

