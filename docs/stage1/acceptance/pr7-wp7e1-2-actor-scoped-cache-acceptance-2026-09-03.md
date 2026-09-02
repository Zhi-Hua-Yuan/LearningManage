# PR7 WP7-E1-2 Actor-scoped Cache 验收

日期：2026-09-03

状态：`PASS / COMPLETED / MERGED / CI_PASS`

## 1. 验收结论

E1-2 已完成 CACHE-005～012 的 actor-scoped key 和未知 actor fail-closed。前端 PR #43 已受保护合并，PR CI 与 develop post-merge CI 全部成功。

## 2. 验收项

| 项目 | 结果 |
|---|---|
| 受保护缓存覆盖 | selected project、project list、progress、task list、aggregate、AI draft、today AI order、replan state |
| actor key 命名空间 | `:actor-{encodeURIComponent(actorId)}` |
| 无 actor 读写 | fail closed，PASS |
| 多账号隔离 | actor 1/2 互不读取对方缓存，PASS |
| 旧无账号 key | 不读取、不复制、不升级，PASS |
| 其他 actor 全量清理 | 不受影响，PASS |
| 全量 Vitest | 53 files / 388 tests passed |
| storage policy | 14 tests passed；52 production accesses covered |
| API 合同 | 44 operations；SHA-256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |

## 3. 受保护 PR 与 CI

| 项目 | 值 |
|---|---|
| PR | [#43](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/43) |
| Merge SHA | `134994685f53287f4c4919259894dfb4d4c86180` |
| PR CI | `33665054286` |
| post-merge CI | [33665308048](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33665308048) |
| post-merge jobs | `100365470531` / `100365532366` / `100365931045` |
| 结论 | 三项 required checks 全部 `success` |

## 4. 边界

E1-2 不删除旧 key，不接入 logout/401 全局清理，不关闭 `S1-R-013`。下一主目标为 WP7-E1-3。

```text
WP7-E1-1：PASS / COMPLETED / MERGED / CI_PASS
WP7-E1-2：PASS / COMPLETED / MERGED / CI_PASS
WP7-E1-3：PENDING
WP7-E2：PENDING
S1-R-013：OPEN
下一主目标：WP7-E1-3
```
