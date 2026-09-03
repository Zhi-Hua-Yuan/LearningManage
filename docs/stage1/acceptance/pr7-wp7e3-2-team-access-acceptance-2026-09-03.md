# PR7 / WP7-E3-2 团队访问裁剪与跨页面回归验收

日期：2026-09-03
状态：PASS / COMPLETED（修复提交 `f1f926e`；基线提交 `cc90c9f`）

## 1. 范围

- 团队项目/成员强制刷新会使旧请求失效，同时保留最近一次成功记录作为只读快照。
- 团队列表变化会裁剪侧栏中的失效团队。
- 任务页持续停留在团队项目路由时，确认团队或项目失权后清空任务上下文并回退到可访问的个人项目。
- 同一项目 ID 在不同账号之间不复用旧账号的团队项目数据。
- 不涉及 WP7-E3-3 的 focus/visibility 刷新和最新 capability 替换。

## 2. 证据

前端变更：`src/stores/collaboration.ts`、`src/stores/collaboration.test.ts`、`src/layout/BasicLayout.vue`、`src/views/task/TaskList.vue`。

自动化结果：

| 门禁 | 结果 |
| --- | --- |
| 受影响任务页测试 | 36 passed |
| 协作存储测试 | 20 passed |
| 前端全量 `npm run test:ci` | 58 files / 454 tests passed |
| `npm run type-check` | PASS |
| `npm run lint:ci` | PASS |
| `npm run build` | PASS |
| `npm run contract:test` / `contract:verify` | 3 passed / 44 operations valid |
| `npm run test:storage-policy` | 14 passed |

新增回归覆盖：同 ID 团队项目的旧账号迟到响应不得覆盖新账号数据；分页项目在后续页时不会误回退；加载期间发生失权会在 loading 结束后重新裁决；已有测试继续覆盖团队消失时项目/成员桶裁剪、失权响应不复活和网络错误保留旧记录。

## 3. 验收结论

```text
WP7-E3-2：PASS / COMPLETED
PR7-T-044：PASS
S7-GAP-010：RUNTIME_CLOSED
S7-GAP-011：OPEN（转 WP7-E3-3）
S1-R-013：OPEN（等待 E3-3 与 E3 总体验收）
下一主目标：WP7-E3-3
```
