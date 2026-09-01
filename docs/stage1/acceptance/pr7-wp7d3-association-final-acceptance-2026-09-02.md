# PR7 / WP7-D3：周复盘关联资源最终验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-02

## 1. 验收范围

本记录覆盖 WP7-D3 的关联资源基础和页面集成：

- PRIVATE/TEAM 范围内的项目与任务候选加载；
- 重点项目单选和跨项目任务关联；
- 任务去重、稳定排序和 500 条上限；
- TEAM A → TEAM B 的关联清理与旧响应隔离；
- 编辑时以后端最新可读关联为准，不恢复失权 ID；
- 关联选择器的受控状态、分页和可访问性行为。

D3 不改变后端 API、数据库、44 operation 合同或成员终止 UI 范围。

## 2. 前端受保护合并证据

| 子项 | PR | Head SHA | Merge SHA | 合并后 CI |
|---|---|---|---|---|
| D3-1 关联基础 | [前端 PR #29](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/29) | `e34faf34c31623e8726853514162c336807e0b10` | `b4f94dd93df67a0197d9d16784acea6dc9fa0210` | [33531449376](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33531449376) |
| D3-2 关联选择器与页面集成 | [前端 PR #30](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/30) | `230bc3afd89021f04488bf786c2a64c347f4a961` | `a9412cfc76ce824e33c3a46fe0822bc5e6ba275a` | [33531717598](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33531717598) |

两个合并后的 `develop` push workflow 均为 `SUCCESS`，并且 head SHA 与对应 Merge SHA 完全匹配。每个 CI 均包含 guard、tests/static verification 和 production build 三个 job。

## 3. 自动化验收结果

### D3 聚焦门禁

- `PR7-T-034`：`PASS`；TEAM A → TEAM B 先清空旧关联，旧响应不会污染新上下文；
- `PR7-T-035`：`PASS`；第 501 个任务被阻止，500 条时仍可移除已选任务；
- `PR7-T-039`：`PASS`；服务端过滤的失权项目或任务不会从旧表单或候选状态恢复。

### 前端本地门禁

- 聚焦测试：5 files / 43 tests passed；
- 全量测试：43 files / 317 tests passed；
- `npm run contract:test`：通过；
- `npm run contract:verify`：通过；
- `npm run type-check`：通过；
- `npm run lint:ci`：通过；
- `npm run build`：通过；
- `git diff --check`：通过。

## 4. API 合同不变性

```text
operations: 44
sha256: 4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6
```

本次没有新增 operation，也没有修改现有 operation 的 method、path 或必填字段。

## 5. 风险边界

- 团队共享卡片的私人字段白名单和统计事实保护仍由 WP7-D 后续 D4/D5 完成；
- 全局缓存、401、登出和多账号身份切换仍由 WP7-E 完成；
- `S1-R-013` 保持 `OPEN`，本次只证明关联页面的 stale response 和失权 ID 不恢复。

## 6. 结论

WP7-D3 的代码、测试、受保护合并和合并后 CI 均已完成，可标记为：

```text
WP7-D3：PASS / COMPLETED / MERGED / CI_PASS
```

WP7-D 整体仍保持 `PENDING / 执行中`，因为 D4、D5 和最终 D6 尚未完成。下一主目标切换为 WP7-D4。
