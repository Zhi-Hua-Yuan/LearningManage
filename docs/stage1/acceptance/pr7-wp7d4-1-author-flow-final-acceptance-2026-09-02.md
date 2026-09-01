# PR7 / WP7-D4-1：作者周复盘读写链路最终验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-02

## 1. 验收范围

本记录覆盖 WP7-D4-1 的作者完整复盘读写链路：

- 移除阶段 0 的 `saveReviewApi`、`updateReviewApi` 和 Legacy payload 出口；
- 当前草稿、历史记录和指定详情分别按作者完整类型归一化；
- 当前详情与历史响应全部校验后原子更新作者页面状态；
- 保存和更新只使用冻结的完整 mutation payload；
- 写入成功后重新读取服务端权威详情，不以本地 payload 乐观覆盖；
- 写入成功但权威回读失败时显示独立警告，避免误报写入失败和重复提交。

D4-1 不处理 D4-2 的服务端统计事实修正，不处理 D4-3 的 AI 任务来源治理，不改变后端 API、数据库或 44 operation 合同。

## 2. 前端受保护合并证据

| 子项 | 证据 |
|---|---|
| 前端 PR | [#31](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/31) |
| Head SHA | `81b05b4d77e27a0589fa60295f45fd91aa72abf3` |
| Merge SHA | `693dbbc73003e7363d643f303bdbc201956558a8` |
| PR CI | [33537039511](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33537039511)：`SUCCESS` |
| develop post-merge CI | [33537281903](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33537281903)：`SUCCESS` |

PR CI 与 post-merge CI 的 guard、tests/static verification 和 production build 三项门禁全部通过，且 develop push 的 head SHA 与 Merge SHA 完全匹配。

## 3. 实现结果

### 3.1 Legacy 收口

前端 `src` 下以下符号引用均为 0：

```text
saveReviewApi
updateReviewApi
LegacyPrivateReviewPayload
LegacyPrivateReviewUpdatePayload
```

作者保存和更新只经过 `saveWeeklyReviewApi`、`updateWeeklyReviewApi`。

### 3.2 作者权威回读

作者链路固定为：

```text
/review/current + /review/history
→ Wire 归一化与完整校验
→ 原子更新 currentReview、reviewForm、historyReviews
→ 保存或更新
→ 再次读取服务端权威状态
```

当前周允许 `id=null` 的未保存草稿；历史和指定详情必须具有有效持久化 ID。历史响应不是数组、当前响应缺失 ID 合同或详情 ID 非法时均失败关闭。

### 3.3 写入与回读错误分层

- 写入失败：保留表单并显示保存失败；
- TEAM 目标失权：清理团队目标和关联，保留作者私人文字；
- 写入成功且回读成功：以服务端返回详情重建表单；
- 写入成功但回读失败：显示“保存已完成，但最新内容加载失败”，不重发 mutation。

## 4. 自动化验收结果

### D4-1 聚焦门禁

- 5 个测试文件；
- 56 个测试；
- 0 failures；
- 覆盖作者草稿 save/update 选择、完整 payload、归一化、服务端回读覆盖和回读失败分层。

### 前端全量门禁

| 门禁 | 结果 |
|---|---|
| `npm run test:ci` | 43 files / 319 tests / 0 failures |
| `npm run test:coverage` | PASS |
| Statements | 82.09% |
| Branches | 72.47% |
| Functions | 81.58% |
| Lines | 85.42% |
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

删除的是同一路径的旧前端兼容函数，没有新增或删除后端 operation，也没有改变 method、path 或必填字段。

## 6. 风险与阶段边界

- `completedTaskCount`、`focusProjectName` 的服务端事实保护属于 D4-2；
- AI 显式任务优先、fallback 过滤和权限错误治理属于 D4-3；
- 团队共享白名单卡片属于 D5；
- 全局 401、登出和多账号缓存隔离属于 WP7-E；
- `S1-R-013` 保持 `OPEN`。

## 7. 结论

WP7-D4-1 的实现、测试、受保护合并和合并后 CI 均已完成，可标记为：

```text
WP7-D4-1：PASS / COMPLETED / MERGED / CI_PASS
```

WP7-D 与 D4 整体继续保持 `PENDING / 执行中`。下一主目标为 WP7-D4-2。
